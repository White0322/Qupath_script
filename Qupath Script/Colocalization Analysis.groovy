import qupath.lib.objects.PathObject
import qupath.lib.regions.RegionRequest
import static qupath.lib.gui.scripting.QPEx.*

// 获取当前图像服务器
def server = getCurrentServer()

// 获取分析区域（使用所有Annotation或选中的ROI）
def annotations = getAnnotationObjects() // 或 getSelectedObjects() 用于特定ROI

// 定义通道和阈值列表（可根据需要扩展）
def channelIndexes = [1, 2] // 通道索引列表，例如 [1, 2] 表示520和570
def channelNames = ["520", "570"] // 通道名称列表，与索引对应
def thresholds = [20, 30] // 阈值列表，与通道对应

// 检查列表长度是否匹配
if (channelIndexes.size() != thresholds.size() || channelIndexes.size() != channelNames.size()) {
    println "错误：通道索引、名称和阈值列表的长度必须相同！"
    return
}

// 遍历每个Annotation
annotations.each { annotation ->
    def roi = annotation.getROI()
    def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
    def img = server.readBufferedImage(request)
    
    // 动态提取所有通道的像素数据
    def pixelData = [:] // 存储每个通道的像素数组
    channelIndexes.eachWithIndex { idx, i ->
        def pixels = new double[img.width * img.height]
        img.getRaster().getSamples(0, 0, img.width, img.height, idx, pixels)
        pixelData[channelNames[i]] = pixels
    }
    
    // 计算所有通道对之间的皮尔逊相关系数和曼德斯系数
    def n = pixelData[channelNames[0]].length // 像素总数
    def means = [:] // 存储每个通道的均值
    pixelData.each { name, pixels ->
        means[name] = pixels.sum() / n
    }
    
    // 皮尔逊相关系数
    def pearsonResults = [:]
    for (int i = 0; i < channelNames.size(); i++) {
        for (int j = i + 1; j < channelNames.size(); j++) {
            def name1 = channelNames[i]
            def name2 = channelNames[j]
            def pixels1 = pixelData[name1]
            def pixels2 = pixelData[name2]
            
            def numerator = 0.0
            def sumSq1 = 0.0
            def sumSq2 = 0.0
            for (int k = 0; k < n; k++) {
                def diff1 = pixels1[k] - means[name1]
                def diff2 = pixels2[k] - means[name2]
                numerator += diff1 * diff2
                sumSq1 += diff1 * diff1
                sumSq2 += diff2 * diff2
            }
            def pearson = numerator / Math.sqrt(sumSq1 * sumSq2)
            pearsonResults["${name1} vs ${name2}"] = pearson
        }
    }
    
    // 曼德斯共定位系数
    def mandersResults = [:]
    for (int i = 0; i < channelNames.size(); i++) {
        for (int j = i + 1; j < channelNames.size(); j++) {
            def name1 = channelNames[i]
            def name2 = channelNames[j]
            def pixels1 = pixelData[name1]
            def pixels2 = pixelData[name2]
            def thresh1 = thresholds[i]
            def thresh2 = thresholds[j]
            
            def sum1 = 0.0
            def sum2 = 0.0
            def coloc1 = 0.0
            def coloc2 = 0.0
            for (int k = 0; k < n; k++) {
                def val1 = pixels1[k]
                def val2 = pixels2[k]
                sum1 += val1
                sum2 += val2
                if (val1 > thresh1 && val2 > thresh2) {
                    coloc1 += val1
                    coloc2 += val2
                }
            }
            mandersResults["M1 (${name1} coloc with ${name2})"] = coloc1 / sum1
            mandersResults["M2 (${name2} coloc with ${name1})"] = coloc2 / sum2
        }
    }
    
    // 将结果存入Annotation的测量值
    def ml = annotation.getMeasurementList()
    pearsonResults.each { key, value ->
        ml.putMeasurement("Pearson Correlation (${key})", value)
    }
    mandersResults.each { key, value ->
        ml.putMeasurement("Manders ${key}", value)
    }
}

// 更新显示
fireHierarchyUpdate()

println "皮尔逊相关系数和曼德斯系数计算完成！"