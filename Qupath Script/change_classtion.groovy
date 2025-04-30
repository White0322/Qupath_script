import static qupath.lib.gui.scripting.QPEx.*

// 获取当前图像数据
def imageData = getCurrentImageData()

// 定义要更改的分类
def positiveClass = getPathClass("Positive")  // 源分类
def negativeClass = getPathClass("Negative")  // 目标分类

// 获取所有注释对象
def allAnnotations = getAnnotationObjects()

// 遍历所有注释对象
allAnnotations.each { anno ->
    // 检查注释是否是glomerulus
    if (anno.getName() == "glomerulus" || anno.getPathClass()?.getName() == "glomerulus") {
        // 获取glomerulus注释下的所有检测对象
        def detections = anno.getChildObjects().findAll { it.isDetection() }
        
        // 遍历检测对象
        detections.each { detection ->
            // 检查检测对象是否是Positive
            if (detection.getPathClass() == positiveClass) {
                // 将Positive检测对象改为Negative
                detection.setPathClass(negativeClass)
            }
        }
    }
}

// 更新显示
fireHierarchyUpdate()

println "Classification change completed!"