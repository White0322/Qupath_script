import ij.IJ
import ij.ImagePlus
import qupath.lib.common.GeneralTools
import qupath.lib.images.servers.ImageServer
import qupath.lib.regions.RegionRequest

// 用户设置
def config = [
    scaleBarWidth: 100.0,          // 标尺长度（微米）
    scaleBarHeight: 20,            // 标尺高度（像素）
    fontSize: 20,                  // 字体大小
    scaleBarColor: "Black",         // 标尺颜色（可改为 White Red Blue Green等）
    location: "Lower Right",       // 标尺位置
    outputDir: buildFilePath(PROJECT_BASE_DIR, "ScalebarPic"), // 输出根目录
    targetClassifications: ["炎细胞浸润", "肺泡间隔增厚"], // 指定导出的分类
    downsample: 1.0                // 下采样因子
]

// 初始化
def server = getCurrentServer()
double pixelWidth = server.getPixelCalibration().getPixelWidthMicrons() * config.downsample
def dirOutput = new File(config.outputDir)
if (!dirOutput.exists() && !dirOutput.mkdirs()) {
    throw new IOException("无法创建输出目录: ${config.outputDir}")
}

// 设置图像属性
def setImageProperties(ImagePlus imp, double pixelWidth) {
    IJ.run(imp, "Properties...", """
        pixel_width=${pixelWidth}
        pixel_height=${pixelWidth}
        unit=µm
        frame=1
        channels=1
        slices=1
    """)
}

// 添加标尺
def addScaleBar(ImagePlus imp, Map config) {
    // 确保 RGB 模式
    if (imp.getType() != ImagePlus.COLOR_RGB) {
        IJ.run(imp, "RGB Color", "")
    }
    
    // 构建标尺命令
    def command = "width=${config.scaleBarWidth} height=${config.scaleBarHeight} font=${config.fontSize} color=${config.scaleBarColor} background=None location=[${config.location}] bold"
    IJ.run(imp, "Scale Bar...", command)
    
    // 合并标尺到图像
    IJ.run(imp, "Flatten", "")
}

// 筛选指定分类的注释
def annotations = getAnnotationObjects().findAll { anno ->
    if (config.targetClassifications.isEmpty()) {
        return true
    }
    def classification = anno.getPathClass()?.getName()
    classification in config.targetClassifications
}

// 处理注释区域
annotations.eachWithIndex { annotation, i ->
    try {
        def request = RegionRequest.createInstance(
            server.getPath(),
            config.downsample,
            annotation.getROI()
        )
        def img = server.readRegion(request)
        
        // 创建分类子文件夹
        def classification = annotation.getPathClass()?.getName() ?: "NoClass"
        def classDir = new File(dirOutput, classification)
        if (!classDir.exists() && !classDir.mkdirs()) {
            throw new IOException("无法创建分类目录: ${classDir.absolutePath}")
        }
        
        // 生成文件名
        def fileName = GeneralTools.stripExtension(server.getMetadata().getName()) +
                       "_${classification}_${i}.tif"
        def fileOutput = new File(classDir, fileName)
        writeImage(img, fileOutput.absolutePath)

        // 添加标尺
        ImagePlus imp = IJ.openImage(fileOutput.absolutePath)
        if (imp) {
            try {
                setImageProperties(imp, pixelWidth)
                addScaleBar(imp, config)
                IJ.saveAs(imp, "Tiff", fileOutput.absolutePath)
                println "成功生成: ${fileOutput.absolutePath} (分类: ${classification}, 下采样: ${config.downsample})"
            } finally {
                imp.close()
            }
        }
    } catch (Exception e) {
        println "处理注释 ${i} (分类: ${annotation.getPathClass()?.getName()}) 出错: ${e.getMessage()}"
    }
}

println "处理完成！共处理 ${annotations.size()} 个注释区域"