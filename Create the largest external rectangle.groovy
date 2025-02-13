import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane

// 获取当前图像中的所有注释
def annotations = getAnnotationObjects()

// 遍历每个注释
annotations.each { annotation ->
    // 获取注释的ROI
    def roi = annotation.getROI()
    
    // 获取ROI所在的平面
    def plane = roi.getImagePlane()
    
    // 获取ROI的边界坐标
    def x = roi.getBoundsX()
    def y = roi.getBoundsY()
    def width = roi.getBoundsWidth()
    def height = roi.getBoundsHeight()
    
    // 创建新的矩形ROI
    def rectangle = ROIs.createRectangleROI(x, y, width, height, plane)
    
    // 创建新的矩形注释
    def newAnnotation = PathObjects.createAnnotationObject(rectangle, annotation.getPathClass())
    
    // 添加新注释到图像
    addObject(newAnnotation)
    
    // 删除原始注释
    removeObject(annotation,true)
}

// 更新显示
fireHierarchyUpdate()