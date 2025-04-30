import qupath.ext.stardist.StarDist2D
import qupath.lib.objects.PathObjects
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.images.servers.TransformedServerBuilder

// 不清空现有检测结果，保留脚本 1 的细胞质对象
selectAnnotations()
println("Step 0: Initialized - " + System.currentTimeMillis())

// 设置变量
def imageData = getCurrentImageData()
def server = getCurrentServer()
def stains = imageData.getColorDeconvolutionStains()
def deconvServer = new TransformedServerBuilder(server).deconvolveStains(stains, 1, 2, 3).build()
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    createSelectAllObject(true)
}
def cal = server.getPixelCalibration()
def downsample = 1.0
println("Step 1: Variables set - " + System.currentTimeMillis())

// 加载保存的细胞质对象
def cytoplasms = getDetectionObjects()
println("Found ${cytoplasms.size()} detection objects in current hierarchy")
if (cytoplasms.isEmpty()) {
    println("Error: No cytoplasm objects found in current hierarchy.")
    return
}
println("Loaded ${cytoplasms.size()} cytoplasm objects - " + System.currentTimeMillis())

// 运行 StarDist
def pathModelStardist = 'F:/Qupath model/stardist/he_heavy_augment.pb'
def stardist = StarDist2D.builder(pathModelStardist)
        .threshold(0.5)
        .normalizePercentiles(1, 99)
        .pixelSize(0.5)
        .cellExpansion(0)
        .measureShape()
        .measureIntensity()
        .includeProbability(true)
        .build()

def start = System.currentTimeMillis()
stardist.detectObjects(imageData, pathObjects)
println("Step 2: StarDist completed - Took " + (System.currentTimeMillis() - start) + " ms")
def nuclei = getDetectionObjects().findAll { !cytoplasms.contains(it) }
println("StarDist detected ${nuclei.size()} nucleus objects")

// 创建细胞
def cells = []
start = System.currentTimeMillis()
cytoplasms.eachWithIndex { cytoplasm, idx ->
    def cx = cytoplasm.getROI().getCentroidX()
    def cy = cytoplasm.getROI().getCentroidY()
    def matchingNucleus = nuclei.find { nucleus ->
        def nx = nucleus.getROI().getCentroidX()
        def ny = nucleus.getROI().getCentroidY()
        def contained = cytoplasm.getROI().contains(nx, ny)
        if (idx < 5) { // 调试前 5 个
            println("Cytoplasm ${idx}: (${cx}, ${cy}), Nucleus: (${nx}, ${ny}), Contained: ${contained}")
        }
        contained
    }
    if (matchingNucleus) {
        cells.add(PathObjects.createCellObject(cytoplasm.getROI(), matchingNucleus.getROI(), getPathClass("Tumor"), null))
    }
}
println("Step 3: Created ${cells.size()} cells - Took " + (System.currentTimeMillis() - start) + " ms")

// 清理并添加所有对象
clearDetections()
addObjects(cells)
println("Step 4: Cells added to hierarchy - " + System.currentTimeMillis())

// 释放中间变量
cytoplasms = null
nuclei = null
stardist.close()
println("Step 5: Resources released - " + System.currentTimeMillis())

// 添加测量
def measurements = [ObjectMeasurements.Measurements.MEAN, ObjectMeasurements.Measurements.STD_DEV] as List
def compartments = ObjectMeasurements.Compartments.values() as List
def shape = ObjectMeasurements.ShapeFeatures.values() as List
start = System.currentTimeMillis()
for (cell in cells) {
    ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
    ObjectMeasurements.addIntensityMeasurements(deconvServer, cell, downsample, measurements, compartments)
    ObjectMeasurements.addCellShapeMeasurements(cell, cal, shape)
}
println("Step 6: Measurements added - Took " + (System.currentTimeMillis() - start) + " ms")

// 完成
fireHierarchyUpdate()
println("StarDist and cell processing done! - " + System.currentTimeMillis())