// 获取所有注释对象
def annotations = getAnnotationObjects()
println "共有 ${annotations.size()} 个注释"

// 遍历所有注释，计算长宽比
for (annotation in annotations) {
    def roi = annotation.getROI()
    if (roi != null) {
        // 获取ROI的外接矩形信息
        def minX = roi.getBoundsX()
        def minY = roi.getBoundsY()
        def width = roi.getBoundsWidth()
        def height = roi.getBoundsHeight()

        if (width > 0 && height > 0) {
            def aspectRatio = width / height
            annotation.getMeasurementList().putMeasurement("Aspect Ratio", aspectRatio)
            
            // 修正：使用 getName() 代替 getPathName()
            println "注释 ${annotation.getName()} 的长宽比为：${aspectRatio}"
        } else {
            println "警告：注释 ${annotation.getName()} 的外接矩形尺寸异常"
        }
    }
}
