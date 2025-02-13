import qupath.lib.objects.PathObject
import qupath.lib.objects.classes.PathClassFactory
import static qupath.lib.gui.scripting.QPEx.*

// 直接在脚本中设置面积阈值（单位：µm²）
def threshold = 40000  // 修改这个值来设置您的阈值

def largeClass = PathClassFactory.getPathClass("tubule")
def smallClass = PathClassFactory.getPathClass("glomerulus")

// 获取当前图像数据
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def calibration = imageData.getServer().getPixelCalibration()

// 获取所有注释对象
def allAnnotations = getAnnotationObjects()

// 统计变量
def largeCount = 0
def smallCount = 0

// 创建一个列表来存储修改后的对象
def modifiedObjects = []

// 处理每个注释
allAnnotations.each { anno ->
    // 计算面积（µm²）
    def pixelArea = anno.getROI().getArea()
    def scaleX = calibration.getPixelWidthMicrons()
    def scaleY = calibration.getPixelHeightMicrons()
    def area = pixelArea * scaleX * scaleY
    
    // 设置分类
    def newClass = area > threshold ? largeClass : smallClass
    if (anno.getPathClass() != newClass) {
        anno.setPathClass(newClass)
        modifiedObjects << anno
        if (area > threshold) {
            largeCount++
        } else {
            smallCount++
        }
    }
}

// 批量更新显示
if (!modifiedObjects.isEmpty()) {
    hierarchy.fireObjectsChangedEvent(this, modifiedObjects)
}

// 显示结果
println "Classification completed:"
println "- Large annotations (> ${threshold} µm²): ${largeCount}"
println "- Small annotations (≤ ${threshold} µm²): ${smallCount}"