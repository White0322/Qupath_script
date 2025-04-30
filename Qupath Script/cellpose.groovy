import qupath.ext.biop.cellpose.Cellpose2D
import qupath.opencv.ops.ImageOps
import qupath.lib.images.servers.TransformedServerBuilder
import qupath.lib.gui.QuPathGUI

// 清空现有检测结果
clearDetections()
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
println("Step 1: Variables set - " + System.currentTimeMillis())

// 运行 Cellpose
def pathModelCellpose = 'cyto2'
def cellpose = Cellpose2D.builder(pathModelCellpose)
        .preprocess(
            ImageOps.Channels.deconvolve(stains),
            ImageOps.Channels.extract(0) // 伊红通道
        )
        .normalizePercentiles(1, 99)
        .pixelSize(0.5)
        .diameter(40)
        .tileSize(512)
        .build()

def start = System.currentTimeMillis()
cellpose.detectObjects(imageData, pathObjects)
println("Step 2: Cellpose completed - Took " + (System.currentTimeMillis() - start) + " ms")

// 保存细胞质检测结果
def cytoplasms = getDetectionObjects()
println("Cellpose detected ${cytoplasms.size()} cytoplasm objects")
addObjects(cytoplasms) // 将检测结果添加到项目中

// 释放中间变量
cytoplasms = null
cellpose = null
println("Step 3: Resources released - " + System.currentTimeMillis())

// 更新层级并保存项目
fireHierarchyUpdate()
QuPathGUI.getInstance().getProject().syncChanges() // 同步项目更改
println("Step 4: Project saved - " + System.currentTimeMillis())

println("Cellpose script done!")