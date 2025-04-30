import qupath.lib.objects.PathAnnotationObject
import qupath.lib.regions.ImagePlane
import qupath.lib.regions.RegionRequest
import qupath.lib.common.GeneralTools
import javax.imageio.ImageIO

// 定义输出目录（相对于项目根目录）
def outputDir = buildFilePath(PROJECT_BASE_DIR, 'exported_images')
mkdirs(outputDir)

// 定义倍率对应的下采样因子（假设原始图像为40x）
def magnificationScales = [
    '4x': 10.0,   // 40x / 4x = 10
    '10x': 4.0,   // 40x / 10x = 4
    '20x': 2.0,   // 40x / 20x = 2
    '40x': 1.0    // 40x / 40x = 1
]

// 设置导出图像的像素大小（以像素为单位，例如512x512）
def tileSize = 512

// 获取当前图像数据
def imageData = getCurrentImageData()
def server = imageData.getServer()

// 获取所有注释
def annotations = getAnnotationObjects()

// 遍历每个倍率
magnificationScales.each { mag, downsample ->
    println "Exporting images at ${mag} (downsample: ${downsample})"
    
    // 遍历每个注释
    annotations.eachWithIndex { annotation, idx ->
        // 获取注释的ROI和中心点
        def roi = annotation.getROI()
        def centroidX = roi.getCentroidX()
        def centroidY = roi.getCentroidY()
        
        // 获取注释的分类标签（如果没有分类，使用默认名称）
        def className = annotation.getPathClass()?.getName() ?: "NoClass"
        // 清理分类名中的非法字符，但保留中文字符
        className = className.replaceAll("[<>:\"/\\\\|?*]", "_")
        
        // 计算区域请求的宽度和高度（以像素为单位）
        def pixelWidthMicrons = tileSize * downsample
        def pixelHeightMicrons = tileSize * downsample
        
        // 创建以注释中心为中心的区域请求，转换为整数坐标
        def region = RegionRequest.createInstance(
            server.getPath(),
            downsample as double,
            (centroidX - pixelWidthMicrons / 2) as int,
            (centroidY - pixelHeightMicrons / 2) as int,
            pixelWidthMicrons as int,
            pixelHeightMicrons as int,
            ImagePlane.getPlane(0, 0)
        )
        
        // 定义输出文件名
        def imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName())
        def outputFileName = "${imageName}_${mag}_Annotation_${idx+1}_${className}.png"
        def outputPath = buildFilePath(outputDir, outputFileName)
        
        // 导出图像
        try {
            // 读取区域为BufferedImage
            def img = server.readRegion(region)
            // 保存图像到文件
            ImageIO.write(img, "png", new File(outputPath))
            println "Exported: ${outputFileName}"
        } catch (Exception e) {
            println "Error exporting ${outputFileName}: ${e.getMessage()}"
        }
    }
}

println "Export completed!"