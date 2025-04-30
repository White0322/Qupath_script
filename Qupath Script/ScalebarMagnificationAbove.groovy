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
    scaleBarHeight: 15,           // 标尺高度（像素）
    fontSize: 20,                 // 字体大小
    scaleBarColor: "Black",       // 标尺颜色
    location: "Lower Right",      // 标尺位置
    outputDir: buildFilePath(PROJECT_BASE_DIR, "exported_images"), // 输出目录
    targetClassifications: ["炎细胞浸润", "肺泡间隔增厚"], // 指定分类
    tileSize: 1024,               // 图像像素大小（1024x1024）
    magnificationScales: [        // 倍率和下采样因子（假设40x基准）
        '20x': 2.0                // 只导出20x
    ]
]

// 初始化
def server = getCurrentServer()
if (!server) {
    println "Error: No image server found."
    return
}

def dirOutput = new File(config.outputDir)
if (!dirOutput.exists() && !dirOutput.mkdirs()) {
    println "Error: Cannot create output directory: ${config.outputDir}"
    return
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

// 添加标尺（文字在标尺上方）
def addScaleBar(ImagePlus imp, Map config, double downsample, def server) {
    if (imp.getType() != ImagePlus.COLOR_RGB) {
        IJ.run(imp, "RGB Color", "")
    }

    def processor = imp.getProcessor()
    def pixelWidthMicrons = server.getPixelCalibration().getPixelWidthMicrons() * downsample
    def scaleBarWidthPixels = config.scaleBarWidth / pixelWidthMicrons

    def width = imp.getWidth()
    def height = imp.getHeight()
    def xPos = width - scaleBarWidthPixels - 30
    def yPos = height - config.scaleBarHeight - 30
    def textXPos = xPos + 9 // 文字右移 10 像素
    def textYPos = yPos - 5

    if (xPos < 0 || yPos < 0 || xPos + scaleBarWidthPixels > width || yPos > height) {
        xPos = Math.max(10, width - 150)
        yPos = Math.max(10, height - 50)
        textXPos = xPos + 10
        textYPos = yPos - 10
        scaleBarWidthPixels = Math.min(scaleBarWidthPixels, width - xPos - 10)
    }

    processor.setColor(java.awt.Color.decode(getColorHex(config.scaleBarColor)))
    processor.fillRect(xPos as int, yPos as int, scaleBarWidthPixels as int, config.scaleBarHeight as int)

    processor.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, config.fontSize))
    processor.drawString("${config.scaleBarWidth} µm", textXPos as int, textYPos as int)

    imp.setProcessor(processor)
}

// 辅助函数：将颜色名称转换为十六进制
def getColorHex(String colorName) {
    switch (colorName.toLowerCase()) {
        case "black": return "#000000"
        case "white": return "#FFFFFF"
        case "red": return "#FF0000"
        case "green": return "#00FF00"
        case "blue": return "#0000FF"
        default: return "#000000"
    }
}

// 筛选指定分类的注释
def annotations = getAnnotationObjects().findAll { anno ->
    if (config.targetClassifications.isEmpty()) return true
    def classification = anno.getPathClass()?.getName()
    classification in config.targetClassifications
}
if (annotations.isEmpty()) {
    println "Error: No annotations found matching classifications."
    return
}

// 处理每个倍率和注释
config.magnificationScales.each { mag, downsample ->
    double pixelWidth = server.getPixelCalibration().getPixelWidthMicrons() * downsample
    
    annotations.eachWithIndex { annotation, idx ->
        def className = "NoClass"
        try {
            className = annotation.getPathClass()?.getName() ?: "NoClass"
            className = className.replaceAll("[<>:\"/\\\\|?*]", "_")
            
            def roi = annotation.getROI()
            if (!roi) {
                println "Error: Annotation ${idx+1} has no ROI"
                return
            }
            def centroidX = roi.getCentroidX()
            def centroidY = roi.getCentroidY()
            
            def pixelWidthMicrons = config.tileSize * downsample
            def pixelHeightMicrons = config.tileSize * downsample
            
            def region = RegionRequest.createInstance(
                server.getPath(),
                downsample,
                (centroidX - pixelWidthMicrons / 2) as int,
                (centroidY - pixelWidthMicrons / 2) as int,
                pixelWidthMicrons as int,
                pixelHeightMicrons as int,
                ImagePlane.getPlane(0, 0)
            )
            
            def classDir = new File(dirOutput, className)
            if (!classDir.exists() && !classDir.mkdirs()) {
                println "Error: Cannot create directory: ${classDir.absolutePath}"
                return
            }
            
            def imageName = GeneralTools.stripExtension(server.getMetadata().getName())
            def outputFileName = "${imageName}_${mag}_${idx+1}_${className}.tif"
            def outputPath = buildFilePath(classDir.path, outputFileName)
            
            def img = server.readRegion(region)
            if (!img) {
                println "Error: Failed to read region for annotation ${idx+1}"
                return
            }
            def tmpFile = new File(outputPath + ".tmp.png")
            ImageIO.write(img, "png", tmpFile)
            if (!tmpFile.exists() || tmpFile.length() == 0) {
                println "Error: Temporary PNG not created or empty: ${tmpFile.absolutePath}"
                return
            }
            
            ImagePlus imp = IJ.openImage(tmpFile.absolutePath)
            if (!imp) {
                println "Error: Failed to load temporary PNG: ${tmpFile.absolutePath}"
                tmpFile.delete()
                return
            }
            try {
                setImageProperties(imp, pixelWidth)
                addScaleBar(imp, config, downsample, server)
                IJ.saveAs(imp, "Tiff", outputPath)
                println "Exported: ${outputPath}"
            } finally {
                imp.close()
                tmpFile.delete()
            }
            
        } catch (Exception e) {
            println "Error processing Annotation_${idx+1} (分类: ${className ?: 'Unknown'}): ${e.message}"
        }
    }
}