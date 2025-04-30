import qupath.ext.stardist.StarDist2D
import qupath.ext.biop.cellpose.Cellpose2D
import qupath.lib.objects.PathObjects
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.images.servers.TransformedServerBuilder
import qupath.opencv.ops.ImageOps
import qupath.lib.objects.classes.PathClass

// 初始化并清理现有检测
clearDetections()
println("Step 0: Initialized - " + System.currentTimeMillis())

// 获取图像数据和服务器
def imageData = getCurrentImageData()
def server = getCurrentServer()
def stains = imageData.getColorDeconvolutionStains()
def deconvServer = new TransformedServerBuilder(server).deconvolveStains(stains, 1, 2, 3).build()
def cal = server.getPixelCalibration()
def downsample = 1.0

// 根据是否选中 ROI 获取 pathObjects
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    println("No ROI selected, processing all annotations.")
    selectAnnotations()  // 选择所有 Annotation
    pathObjects = getAnnotationObjects()  // 获取所有 Annotation
    if (pathObjects.isEmpty()) {
        println("No annotations found, processing entire image.")
        createSelectAllObject(true)
        pathObjects = getSelectedObjects()
    }
} else {
    println("Processing selected ROI: ${pathObjects.size()} object(s) selected.")
}
println("Step 1: Variables set - " + System.currentTimeMillis())

// 运行 Cellpose
def pathModelCellpose = 'cyto3'
def cellpose = Cellpose2D.builder(pathModelCellpose)
        .preprocess(ImageOps.Channels.deconvolve(stains), ImageOps.Channels.extract(0))  // 通道 0 (DAB 或 Eosin)
//        .channels(1)
        .normalizePercentiles(1, 99)
        .pixelSize(0.5)
        .diameter(20)
        .tileSize(256)
        .build()
def start = System.currentTimeMillis()
cellpose.detectObjects(imageData, pathObjects)
println("Step 2: Cellpose completed - Took " + (System.currentTimeMillis() - start) + " ms")
def cytoplasms = getDetectionObjects()
println("Cellpose detected ${cytoplasms.size()} cytoplasm objects")

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
start = System.currentTimeMillis()
stardist.detectObjects(imageData, pathObjects)
println("Step 3: StarDist completed - Took " + (System.currentTimeMillis() - start) + " ms")
def nuclei = getDetectionObjects().findAll { !cytoplasms.contains(it) }
println("StarDist detected ${nuclei.size()} nucleus objects")

// 创建细胞
def cells = []
start = System.currentTimeMillis()
def usedNuclei = []
cytoplasms.eachWithIndex { cytoplasm, idx ->
    def cx = cytoplasm.getROI().getCentroidX()
    def cy = cytoplasm.getROI().getCentroidY()
    def matchingNucleus = nuclei.find { nucleus ->
        def nx = nucleus.getROI().getCentroidX()
        def ny = nucleus.getROI().getCentroidY()
        def contained = cytoplasm.getROI().contains(nx, ny)
        if (idx < 5) {
//            println("Cytoplasm ${idx}: (${cx}, ${cy}), Nucleus: (${nx}, ${ny}), Contained: ${contained}")
        }
        contained
    }
    if (matchingNucleus) {
        cells.add(PathObjects.createCellObject(cytoplasm.getROI(), matchingNucleus.getROI(), getPathClass("Tumor"), null))
        usedNuclei.add(matchingNucleus)
    }
}

// 添加未匹配的“只有核”的细胞
def unmatchedNuclei = nuclei.findAll { !usedNuclei.contains(it) }
unmatchedNuclei.each { nucleus ->
    cells.add(PathObjects.createCellObject(nucleus.getROI(), nucleus.getROI(), getPathClass("Other"), null))
}
println("Step 4: Created ${cells.size()} cells (including ${unmatchedNuclei.size()} nucleus-only cells) - Took " + (System.currentTimeMillis() - start) + " ms")

// 清理并添加细胞对象
clearDetections()
addObjects(cells)
println("Step 5: Cells added to hierarchy - " + System.currentTimeMillis())

// 释放中间变量
cytoplasms = null
nuclei = null
usedNuclei = null
unmatchedNuclei = null
cellpose = null
stardist.close()
System.gc()
println("Step 6: Resources released - " + System.currentTimeMillis())

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
println("Step 7: Measurements added - Took " + (System.currentTimeMillis() - start) + " ms")

// 完成
fireHierarchyUpdate()
println("Processing done! - " + System.currentTimeMillis())