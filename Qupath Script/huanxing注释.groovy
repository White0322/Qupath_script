import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools
import qupath.lib.objects.classes.PathClass

// 获取当前图像数据和层级
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// 获取所有标注对象
def annotations = hierarchy.getAnnotationObjects()

// 如果没有 Annotation，退出
if (annotations.isEmpty()) {
    println("No annotations found in the current image.")
    return
}

// 第一步：识别并分类内外环
def outerToInnerMap = [:]
annotations.each { outer ->
    def outerROI = outer.getROI()
    annotations.each { inner ->
        if (outer != inner) {
            def innerROI = inner.getROI()
            if (outerROI.contains(innerROI.getCentroidX(), innerROI.getCentroidY()) && 
                outerROI.getArea() > innerROI.getArea()) {
                def allPointsInside = innerROI.getAllPoints().every { point ->
                    outerROI.contains(point.x, point.y)
                }
                if (allPointsInside) {
                    outerToInnerMap[outer] = inner
                }
            }
        }
    }
}

// 为内外环分配分类
outerToInnerMap.each { outer, inner ->
    outer.setPathClass(getPathClass("Ex"))
    inner.setPathClass(getPathClass("In"))
}
println("Classified ${outerToInnerMap.size()} donut-like annotations")

// 第二步：创建 Wall 区域并删除原始标注
def exAnnotations = annotations.findAll { it.getPathClass()?.getName() == "Ex" }
def inAnnotations = annotations.findAll { it.getPathClass()?.getName() == "In" }
def wallAnnotations = []
def toRemove = []

inAnnotations.each { inAnn ->
    def inROI = inAnn.getROI()
    def inPlane = inROI.getImagePlane()
    def exAnn = exAnnotations.find { ex ->
        def exROI = ex.getROI()
        exROI.getImagePlane() == inPlane && 
        exROI.getGeometry().covers(inROI.getGeometry())
    }
    if (exAnn) {
        def exGeo = exAnn.getROI().getGeometry()
        def inGeo = inROI.getGeometry()
        def wallGeo = exGeo.difference(inGeo)
        if (!wallGeo.isEmpty()) {
            def wallROI = GeometryTools.geometryToROI(wallGeo, inPlane)
            def wallClass = PathClass.fromString("Positive")
            def wallAnn = PathObjects.createAnnotationObject(wallROI, wallClass)
            wallAnnotations.add(wallAnn)
            toRemove.addAll([exAnn, inAnn])
        }
    }
}

// 添加新标注并删除原始标注
hierarchy.addPathObjects(wallAnnotations)
if (!toRemove.isEmpty()) {
    hierarchy.removeObjects(toRemove, true)
}

// 刷新显示
hierarchy.fireHierarchyChangedEvent(this)
println("Generated ${wallAnnotations.size()} Wall regions, removed ${toRemove.size()} original annotations")
println("Processing completed!")