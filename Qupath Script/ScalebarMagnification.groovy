import qupath.lib.objects.PathAnnotationObject
import qupath.lib.regions.ImagePlane
import qupath.lib.regions.RegionRequest
import qupath.lib.common.GeneralTools
import javax.imageio.ImageIO
import ij.IJ
import ij.ImagePlus

// 用户设置
def config = [
    scaleBarWidth: 50.0,          // 标尺长度（微米）
    scaleBarHeight: 15,            // 标尺高度（像素）
    fontSize: 20,                  // 字体大小
    scaleBarColor: "Black",        // 标尺颜色
    location: "Lower Right",       // 标尺位置
    outputDir: buildFilePath(PROJECT_BASE_DIR, "exported_images"), // 输出目录
    targetClassifications: ["炎细胞浸润", "肺泡间隔增厚"], // 指定分类
    tileSize: 1024,                 // 图像像素大小（512x512）
    magnificationScales: [         // 倍率和下采样因子（假设40x基准）
        '20x': 2.0                 // 只导出10x，如需其他倍率可添加
        // '4x': 10.0, ‘10x’：4.0，'20x': 2.0, '40x': 1.0
    ]
]

// 初始化
def server = getCurrentServer()
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
    if (imp.getType() != ImagePlus.COLOR_RGB) {
        IJ.run(imp, "RGB Color", "")
    }
    def command = "width=${config.scaleBarWidth} height=${config.scaleBarHeight} font=${config.fontSize} color=${config.scaleBarColor} background=None location=[${config.location}] bold"
    IJ.run(imp, "Scale Bar...", command)
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

// 处理每个倍率和注释
config.magnificationScales.each { mag, downsample ->
    println "Exporting images at ${mag} (downsample: ${downsample})"
    
    double pixelWidth = server.getPixelCalibration().getPixelWidthMicrons() * downsample
    
    annotations.eachWithIndex { annotation, idx ->
        try {
            // 获取注释中心
            def roi = annotation.getROI()
            def centroidX = roi.getCentroidX()
            def centroidY = roi.getCentroidY()
            
            // 计算区域大小（微米）
            def pixelWidthMicrons = config.tileSize * downsample
            def pixelHeightMicrons = config.tileSize * downsample
            
            // 创建区域请求
            def region = RegionRequest.createInstance(
                server.getPath(),
                downsample as double,
                (centroidX - pixelWidthMicrons / 2) as int,
                (centroidY - pixelHeightMicrons / 2) as int,
                pixelWidthMicrons as int,
                pixelHeightMicrons as int,
                ImagePlane.getPlane(0, 0)
            )
            
            // 获取分类
            def className = annotation.getPathClass()?.getName() ?: "NoClass"
            className = className.replaceAll("[<>:\"/\\\\|?*]", "_")
            
            // 创建分类子文件夹
            def classDir = new File(dirOutput, className)
            if (!classDir.exists() && !classDir.mkdirs()) {
                throw new IOException("无法创建分类目录: ${classDir.absolutePath}")
            }
            
            // 定义文件名
            def imageName = GeneralTools.stripExtension(server.getMetadata().getName())
            def outputFileName = "${imageName}_${mag}_${idx+1}_${className}.tif"
            def outputPath = buildFilePath(classDir.path, outputFileName)
            
            // 导出图像
            def img = server.readRegion(region)
            ImageIO.write(img, "png", new File(outputPath + ".tmp.png"))
            
            // 添加标尺
            ImagePlus imp = IJ.openImage(outputPath + ".tmp.png")
            if (imp) {
                try {
                    setImageProperties(imp, pixelWidth)
                    addScaleBar(imp, config)
                    IJ.saveAs(imp, "Tiff", outputPath)
                    println "Exported: ${outputPath} (分类: ${className}, 倍率: ${mag})"
                } finally {
                    imp.close()
                }
            }
            
            // 删除临时文件
            new File(outputPath + ".tmp.png").delete()
            
        } catch (Exception e) {
            println "Error processing Annotation_${idx+1} (分类: ${className}, ${mag}): ${e.getMessage()}"
        }
    }
}

println "处理完成！共处理 ${annotations.size()} 个注释区域"