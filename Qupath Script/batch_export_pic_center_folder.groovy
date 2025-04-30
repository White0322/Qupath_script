import qupath.imagej.tools.IJTools
import qupath.lib.gui.images.servers.RenderedImageServer
import qupath.lib.gui.viewer.overlays.HierarchyOverlay
import qupath.lib.regions.RegionRequest
import ij.IJ
import ij.gui.Roi
import static qupath.lib.gui.scripting.QPEx.*

// 设置下采样率
double downsample = 1.0  // 保持原始分辨率

// 设置导出图像大小
int exportSize = 1300  // 导出1300x1300像素的图像

// 创建主输出目录
def mainOutputDir = buildFilePath(PROJECT_BASE_DIR, 'Exported_Regions')
mkdirs(mainOutputDir)

// 获取图像名称（用于创建子文件夹）
def imageName = GeneralTools.getNameWithoutExtension(getCurrentImageData().getServer().getMetadata().getName())

// 为当前切片创建输出目录
def sliceOutputDir = buildFilePath(mainOutputDir, imageName)
mkdirs(sliceOutputDir)

def pathOutput_region_RGB = buildFilePath(sliceOutputDir, 'region_RGB')
mkdirs(pathOutput_region_RGB)
def pathOutput_region_original = buildFilePath(sliceOutputDir, 'region_original')
mkdirs(pathOutput_region_original)

// 获取当前视图和图像数据
def viewer = getCurrentViewer()
def imageData = getCurrentImageData()
def server = getCurrentServer()

// 创建渲染图像服务器
def renderedServer = new RenderedImageServer.Builder(imageData)
    .downsamples(downsample)
    .layers(new HierarchyOverlay(viewer.getImageRegionStore(), viewer.getOverlayOptions(), imageData))
    .build()

// 获取所有注释对象
def allAnnotations = getAnnotationObjects()

// 处理每个注释对象
allAnnotations.eachWithIndex{ anno, x ->
    try {
        // 获取分类名称
        def className = anno.getPathClass() ? anno.getPathClass().getName() : "Unclassified"

        // 生成文件名
        def name = anno.getName() ?: "annotation_" + x
        def fileName = pathOutput_region_RGB + "//" + className + "-" + name + "-" + imageName + "-" + x + ".png"
        def fileName2 = pathOutput_region_original + "//" + className + "-" + name + "-" + imageName + "-" + x + ".png"
        
        // 获取ROI
        def roi = anno.getROI()
        
        // 获取ROI的中心坐标
        def centerX = roi.getCentroidX()
        def centerY = roi.getCentroidY()

        // 计算以中心点为中心的边界坐标
        def startX = (centerX - exportSize/2) as int
        def startY = (centerY - exportSize/2) as int

        // 创建RegionRequest
        def request = RegionRequest.createInstance(
            server.getPath(),
            downsample,
            startX,
            startY,
            exportSize,
            exportSize,
            roi.getImagePlane()
        )

        // 导出RGB图像
        def pathImage = IJTools.convertToImagePlus(renderedServer, request)
        def img = pathImage.getImage()
        IJ.save(img, fileName)

        // 导出原始图像
        def pathImage2 = IJTools.convertToImagePlus(server, request)
        def img2 = pathImage2.getImage()
        IJ.save(img2, fileName2)
        
        println "Exported: " + fileName
    } catch (Exception e) {
        println "Failed to export annotation " + x + ": " + e.getMessage()
    }
}

println "Export completed!"