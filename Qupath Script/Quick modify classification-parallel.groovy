import qupath.lib.objects.classes.PathClass

def start = System.currentTimeMillis()
def pointAnnotations = getAnnotationObjects()
def pointData = []
pointAnnotations.each { annotation ->
    def className = annotation.getPathClass()?.getName()
    if (className) {
        def points = annotation.getROI().getAllPoints()
        points.each { point ->
            pointData << [x: point.getX(), y: point.getY(), className: className]
        }
    }
}

def detections = getDetectionObjects()
detections.parallelStream().each { detection ->
    def detectionROI = detection.getROI()
    String assignedClass = null
    
    for (point in pointData) {
        if (detectionROI.contains(point.x, point.y)) {
            assignedClass = point.className
            break
        }
    }
    
    if (assignedClass != null) {
        synchronized(detection) { // 同步以避免并发问题
            detection.setPathClass(PathClass.getInstance(assignedClass))
        }
    }
}

fireHierarchyUpdate()
println("分类完成！")
def end = System.currentTimeMillis()
println "耗时：${(end - start) / 1000} 秒"