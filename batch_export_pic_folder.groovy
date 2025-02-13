import qupath.imagej.tools.IJTools
import qupath.lib.gui.images.servers.RenderedImageServer
import qupath.lib.gui.viewer.overlays.HierarchyOverlay
import qupath.lib.regions.RegionRequest
import ij.IJ
import ij.gui.Roi
import static qupath.lib.gui.scripting.QPEx.*

// 设置下采样率
double downsample = 5

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
def server2 = getCurrentServer()

// 创建渲染图像服务器
def server = new RenderedImageServer.Builder(imageData)
    .downsamples(downsample)
    .layers(new HierarchyOverlay(viewer.getImageRegionStore(), viewer.getOverlayOptions(), imageData))
    .build()

//// 获取所有未分类的注释对象
//def unclassifiedAnnotations = getAnnotationObjects().findAll{it.getPathClass() == null}


//// 指定要导出的分类名称（可以指定多个）
//def targetClasses = ["Tumor", "Stroma"]  // 修改为您需要的分类名称
//
//// 获取指定分类的注释对象
//def filteredAnnotations = getAnnotationObjects().findAll { anno ->
//    def pathClass = anno.getPathClass()
//    pathClass && targetClasses.contains(pathClass.getName())


// 获取所有注释对象（不再限制未分类）
def allAnnotations = getAnnotationObjects()

// 处理每个注释对象
allAnnotations.eachWithIndex{ anno, x ->
    try {
        // 获取分类名称
        def className = anno.getPathClass() ? anno.getPathClass().getName() : "Unclassified"

        // 生成文件名
        def name = anno.getName() ?: + x
        def fileName = pathOutput_region_RGB + "//" + className + "-" + name + "-" + imageName + "-" + x + ".png"
        def fileName2 = pathOutput_region_original + "//" + className + "-" + name + "-" + imageName + "-" + x + ".png"
        
        // 获取ROI
        def roi = anno.getROI()
        def requestROI = RegionRequest.createInstance(getCurrentServer().getPath(), downsample, roi)
        
        // 转换图像
        def pathImage = IJTools.convertToImagePlus(server, requestROI)
        def pathImage2 = IJTools.convertToImagePlus(server2, requestROI)
        
        // 正确转换ROI
        def roiIJ = IJTools.convertToIJRoi(roi, pathImage)
        
        // 获取ImageJ图像
        def imgMasked = pathImage.getImage()
        def imgOriginal = pathImage2.getImage()
        
        // 应用ROI并裁剪图像
        imgMasked.setRoi(roiIJ)
        imgOriginal.setRoi(roiIJ)
        imgMasked = imgMasked.crop()
        imgOriginal = imgOriginal.crop()
        
        // 保存图像
        IJ.save(imgMasked, fileName)
        IJ.save(imgOriginal, fileName2)
        
        println "Exported: " + fileName
    } catch (Exception e) {
        println "Failed to export annotation " + x + ": " + e.getMessage()
    }
}

println "Export completed!"