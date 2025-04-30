import qupath.lib.objects.PathObject
import static qupath.lib.gui.scripting.QPEx.*

// 指定要删除的分类名称（可以指定多个）
def classesToRemove = ["Positive", "None"]  // 添加"None"表示未分类的注释

// 获取当前图像数据
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// 获取所有注释对象
def allAnnotations = getAnnotationObjects()

// 查找匹配的注释
def annotationsToRemove = allAnnotations.findAll { anno ->
    def pathClass = anno.getPathClass()
    if (classesToRemove.contains("None")) {
        // 如果包含"None"，则匹配未分类的注释
        pathClass == null || (pathClass && classesToRemove.contains(pathClass.getName()))
    } else {
        // 否则只匹配指定分类
        pathClass && classesToRemove.contains(pathClass.getName())
    }
}

// 显示统计信息
println "Found ${annotationsToRemove.size()} annotations to remove:"
annotationsToRemove.each { anno ->
    def className = anno.getPathClass() ? anno.getPathClass().getName() : "Unclassified"
    println "- ${className}: ${anno.getName() ?: 'Unnamed'}"
}

// 直接执行删除操作
if (annotationsToRemove) {
    hierarchy.removeObjects(annotationsToRemove, true)
    println "Successfully removed ${annotationsToRemove.size()} annotations."
} else {
    println "No matching annotations found."
}