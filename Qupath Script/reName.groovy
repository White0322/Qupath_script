import qupath.lib.objects.PathObject
import static qupath.lib.gui.scripting.QPEx.*

// 获取当前图像数据
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// 获取所有注释对象
def allAnnotations = getAnnotationObjects()

// 创建一个map来存储每个分类的计数器
def classCounters = [:]

// 创建一个列表来存储修改后的对象
def modifiedObjects = []

// 处理每个注释
allAnnotations.each { anno ->
    def pathClass = anno.getPathClass()
    def className = pathClass ? pathClass.getName() : "Unclassified"
    
    // 初始化分类计数器
    if (!classCounters.containsKey(className)) {
        classCounters[className] = 0
    }
    
    // 跳过未分类的注释
    if (className != "Unclassified") {
        // 增加计数器
        classCounters[className]++
        
        // 设置新名称
        def newName = "${className}_${classCounters[className]}"
        anno.setName(newName)
        modifiedObjects << anno
    }
}

// 批量更新显示
if (!modifiedObjects.isEmpty()) {
    hierarchy.fireObjectsChangedEvent(this, modifiedObjects)
}

// 显示结果
println "Renaming completed:"
classCounters.each { className, count ->
    if (className != "Unclassified") {
        println "- ${className}: ${count} annotations renamed"
    }
}
println "- Unclassified annotations remain unchanged"