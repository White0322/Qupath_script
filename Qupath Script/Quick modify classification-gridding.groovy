import qupath.lib.objects.classes.PathClass

def start = System.currentTimeMillis()
// 预处理点数据并按网格分组
def pointAnnotations = getAnnotationObjects()
def gridSize = 100.0 // 网格大小，可根据切片调整
def pointGrid = [:] // 网格存储：key是网格坐标，value是点列表

pointAnnotations.each { annotation ->
    def className = annotation.getPathClass()?.getName()
    if (className) {
        try {
            def points = annotation.getROI().getAllPoints()
            if (points && !points.isEmpty()) {
                points.each { point ->
                    def x = point.getX()
                    def y = point.getY()
                    def gridX = (x / gridSize).toInteger()
                    def gridY = (y / gridSize).toInteger()
                    def key = "${gridX},${gridY}"
                    pointGrid[key] = pointGrid.get(key, []) << [x: x, y: y, className: className]
                }
            }
        } catch (Exception e) {
            println "警告：处理Annotation ${annotation} 时出错: ${e.message}"
        }
    }
}
println "网格划分完成，找到 ${pointGrid.values().flatten().size()} 个点"

// 获取所有检测对象
def detections = getDetectionObjects()

// 遍历每个检测对象
detections.each { detection ->
    def detectionROI = detection.getROI()
    String assignedClass = null
    
    try {
        // 手动计算边界框
        def boundsX = detectionROI.getBoundsX()
        def boundsY = detectionROI.getBoundsY()
        def boundsWidth = detectionROI.getBoundsWidth()
        def boundsHeight = detectionROI.getBoundsHeight()
        
        def minGridX = (boundsX / gridSize).toInteger()
        def maxGridX = ((boundsX + boundsWidth) / gridSize).toInteger()
        def minGridY = (boundsY / gridSize).toInteger()
        def maxGridY = ((boundsY + boundsHeight) / gridSize).toInteger()
        
        for (gx in minGridX..maxGridX) {
            for (gy in minGridY..maxGridY) {
                def key = "${gx},${gy}"
                def pointsInGrid = pointGrid[key]
                if (pointsInGrid) {
                    for (point in pointsInGrid) {
                        if (detectionROI.contains(point.x, point.y)) {
                            assignedClass = point.className
                            break
                        }
                    }
                }
                if (assignedClass != null) break
            }
            if (assignedClass != null) break
        }
        
        if (assignedClass != null) {
            detection.setPathClass(PathClass.getInstance(assignedClass))
        }
    } catch (Exception e) {
        println "警告：处理Detection ${detection} 时出错: ${e.message}"
    }
}

// 更新显示
fireHierarchyUpdate()
println("分类完成！")


def end = System.currentTimeMillis()
println "耗时：${(end - start) / 1000} 秒"