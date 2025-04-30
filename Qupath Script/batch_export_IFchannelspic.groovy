import qupath.imagej.tools.IJTools
import qupath.lib.regions.RegionRequest
import ij.IJ
import ij.ImagePlus
import ij.process.ColorProcessor
import static qupath.lib.gui.scripting.QPEx.*

double downsample = 1.0
def exportWidth = null
def exportHeight = null

def imageData = getCurrentImageData()
def server = imageData.getServer()
def viewer = getCurrentViewer()
def imageName = server.getMetadata().getName().replaceFirst(~/\.[^\.]+$/, "")
def outputDir = buildFilePath(PROJECT_BASE_DIR, "exported_images", imageName)
mkdirs(outputDir)

// 获取所有 Annotation
def annotations = getAnnotationObjects()
if (annotations.isEmpty()) {
    println "错误：图像中没有Annotation，请先创建Annotation区域！"
    return
}

// 定义要导出的分类（null 表示未分类的 Annotation）
def targetClassifications = [null] // 只导出分类为 null 的 Annotation
// 示例：如果要导出特定分类，可以改为：
// def targetClassifications = ["Positive", "Region of Interest"]
// 或混合使用：def targetClassifications = [null, "Positive"]

// 过滤符合指定分类的 Annotation
def filteredAnnotations = annotations.findAll { annotation ->
    def classification = annotation.getPathClass()?.toString()
    if (targetClassifications.contains(null) && classification == null) {
        return true // 包含未分类的 Annotation
    }
    if (classification != null && targetClassifications.contains(classification)) {
        return true // 包含匹配分类的 Annotation
    }
    println "跳过 Annotation ${annotation.getID()}，分类 ${classification ?: 'null'} 不在目标列表中"
    return false
}

if (filteredAnnotations.isEmpty()) {
    println "错误：没有符合目标分类的Annotation可导出！目标分类：${targetClassifications}"
    return
}

// 获取 ImageDisplay 和通道信息
def display = viewer.getImageDisplay()
def availableChannels = display.availableChannels()

// 定义要导出的通道
def channelsToExport = [
    [name: "DAPI", index: 0],
    [name: "MAP2", index: 1],
    [name: "GAP43", index: 2],
    [name: "GFAP", index: 3]
]

// 验证并过滤有效通道
def nChannels = server.nChannels()
println "图像总通道数：${nChannels}"
def validChannels = channelsToExport.findAll { channel ->
    if (channel.index >= nChannels) {
        println "警告：通道索引 ${channel.index} 超出范围（总通道数：${nChannels}），跳过 ${channel.name}"
        return false
    }
    true
}

if (validChannels.isEmpty()) {
    println "错误：没有有效的通道可导出！"
    return
}

// 为每个有效通道读取 QuPath 中的显示颜色和范围
validChannels.each { channel ->
    def chInfo = availableChannels[channel.index]
    def color = chInfo.getColor()
    def r = (color >> 16) & 0xFF
    def g = (color >> 8) & 0xFF
    def b = color & 0xFF
    channel.color = [r: r, g: g, b: b]
    channel.minDisplay = chInfo.getMinDisplay()
    channel.maxDisplay = chInfo.getMaxDisplay()
    println "通道 ${channel.name} 的颜色：R=${r}, G=${g}, B=${b}"
    println "通道 ${channel.name} 的显示范围：[${channel.minDisplay}, ${channel.maxDisplay}]"
}

// 预计算通道范围
def channelRanges = validChannels.collect { channel ->
    def range = channel.maxDisplay - channel.minDisplay
    if (range == 0) range = 1
    [min: channel.minDisplay, max: channel.maxDisplay, range: range]
}

// 遍历过滤后的 Annotation
filteredAnnotations.eachWithIndex { annotation, idx ->
    def roi = annotation.getROI()
    if (roi == null) {
        println "警告：Annotation ${annotation.getID()} 没有有效的ROI，跳过！"
        return
    }
    def annotationName = annotation.getName() ?: "Annotation_${idx}_${annotation.getPathClass() ?: 'Unclassified'}"
    def request = exportWidth != null && exportHeight != null ?
        RegionRequest.createInstance(server.getPath(), downsample,
            Math.max(0, Math.min(roi.getCentroidX() - exportWidth / 2 as int, server.getWidth() - exportWidth)),
            Math.max(0, Math.min(roi.getCentroidY() - exportHeight / 2 as int, server.getHeight() - exportHeight)),
            exportWidth, exportHeight, roi.getImagePlane()) :
        RegionRequest.createInstance(server.getPath(), downsample, roi)

    // 从服务器读取区域图像
    def img = server.readBufferedImage(request)
    def raster = img.getRaster()
    def width = request.getWidth()
    def height = request.getHeight()
    def pixelCount = width * height

    // 一次性读取所有有效通道的像素数据
    def channelPixels = validChannels.collect { channel ->
        def pixels = new float[pixelCount]
        raster.getSamples(0, 0, width, height, channel.index, pixels)
        pixels
    }

    // 单通道导出
    validChannels.eachWithIndex { channel, i ->
        def pixels = channelPixels[i]
        def minDisplay = channelRanges[i].min
        def maxDisplay = channelRanges[i].max
        def range = channelRanges[i].range

        def cp = new ColorProcessor(width, height)
        def rgbPixels = cp.getPixels() as int[]

        for (int j = 0; j < pixelCount; j++) {
            def pixelValue = Math.max(minDisplay, Math.min(maxDisplay, pixels[j]))
            def intensity = ((pixelValue - minDisplay) * 255 / range) as int
            rgbPixels[j] = (channel.color.r << 16) | (channel.color.g << 8) | channel.color.b
            rgbPixels[j] &= (intensity << 16) | (intensity << 8) | intensity
        }

        def outputPath = buildFilePath(outputDir, "${annotationName}_${channel.name}.png")
        IJ.save(new ImagePlus(channel.name, cp), outputPath)
        println "已导出：${outputPath}"
    }

    // 合并通道导出
    def rgbImg = new ColorProcessor(width, height)
    def rgbPixels = rgbImg.getPixels() as int[]

    for (int i = 0; i < pixelCount; i++) {
        def r = 0, g = 0, b = 0
        validChannels.eachWithIndex { channel, j ->
            def pixels = channelPixels[j]
            def minDisplay = channelRanges[j].min
            def range = channelRanges[j].range
            def pixelValue = Math.max(minDisplay, Math.min(channelRanges[j].max, pixels[i]))
            def intensity = ((pixelValue - minDisplay) * 255 / range) as int
            
            r = Math.min(r + (channel.color.r * intensity / 255 as int), 255)
            g = Math.min(g + (channel.color.g * intensity / 255 as int), 255)
            b = Math.min(b + (channel.color.b * intensity / 255 as int), 255)
        }
        rgbPixels[i] = (r << 16) | (g << 8) | b
    }

    def mergeOutputPath = buildFilePath(outputDir, "${annotationName}_Merged.png")
    IJ.save(new ImagePlus("Merged", rgbImg), mergeOutputPath)
    println "已导出合并图片：${mergeOutputPath}"
}

println "Export completed!"