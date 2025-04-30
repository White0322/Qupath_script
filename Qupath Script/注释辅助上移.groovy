import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs

// 获取当前图像中的所有Annotation
def annotations = getAnnotationObjects()

// 如果没有Annotation，打印提示并退出
if (annotations.isEmpty()) {
    print "图像中没有找到Annotation！"
    return
}

// 遍历所有Annotation，复制并移动
def newAnnotations = []
annotations.each { annotation ->
    // 复制Annotation
    def newAnnotation = PathObjects.createAnnotationObject(annotation.getROI(), annotation.getPathClass())
    
    // 获取原始ROI
    def roi = newAnnotation.getROI()
    def geom = roi.getGeometry()
    
    // 检查ROI类型并移动
    def movedROI
    if (geom instanceof org.locationtech.jts.geom.Point) {
        // 如果是Point类型，手动计算新坐标并创建PointsROI
        def coord = geom.getCoordinate()
        def newX = coord.x
        def newY = coord.y - 1160  // 向上移动1150px
        movedROI = ROIs.createPointsROI(newX, newY, roi.getImagePlane())
    } else {
        // 对于其他几何类型（如Polygon），使用translate并转换
        def movedGeom = geom.translate(0, -1150)
        movedROI = ROIs.create(movedGeom, roi.getImagePlane())
    }
    
    // 设置移动后的ROI到新Annotation
    newAnnotation.setROI(movedROI)
    
    // 可选：设置新名称以区分
    newAnnotation.setName(annotation.getName() + "_moved")
    
    // 添加到新Annotation列表
    newAnnotations << newAnnotation
}

// 将所有新Annotation添加到图像中
addObjects(newAnnotations)

// 刷新显示
fireHierarchyUpdate()

// 打印完成信息
print "已完成：复制并向上移动1150px的所有Annotation"