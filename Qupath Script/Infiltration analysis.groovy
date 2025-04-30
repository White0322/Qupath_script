// 获取组织限定区域（假设分类为"Tissue"）
def tissueAnnotations = getAnnotationObjects().findAll{it.getPathClass() == getPathClass("Region*")}
if (tissueAnnotations.isEmpty()) {
    println("未找到组织限定区域！请创建分类为'Tissue'的注释")
    return
}

// 获取原始肿瘤区域
def originalTumors = getAnnotationObjects().findAll{it.getPathClass() == getPathClass("Tumor")}
if (originalTumors.isEmpty()) {
    println("未找到分类为'Tumor'的注释！")
    return
}

// 设置外扩距离（微米）
double expandMicrons = 200.0
double pixelSize = getCurrentServer().getPixelCalibration().getAveragedPixelSizeMicrons()
double expandPixels = expandMicrons / pixelSize

// 创建列表存储结果
def resultAnnotations = []

// 处理每个肿瘤区域
originalTumors.each { tumor ->
    // 获取对应的组织区域（找到与当前肿瘤相交的组织）
    def parentTissue = tissueAnnotations.find { tissue -> 
        try {
            def intersection = RoiTools.intersection(tissue.getROI(), tumor.getROI())
            return intersection != null && !intersection.isEmpty()
        } catch (Exception e) {
            println("检查相交时出错: " + e)
            return false
        }
    }
    
    if (!parentTissue) {
        println("未找到包含肿瘤的组织区域！跳过此肿瘤")
        return
    }
    
    try {
        // 1. 使用正确类型转换创建外扩区域
        def roi = tumor.getROI()
        def shape = roi.getShape()
        
        // 确保使用float类型参数
        float strokeWidth = expandPixels.toFloat() * 2
        def stroke = new java.awt.BasicStroke(strokeWidth)
        def expandedShape = stroke.createStrokedShape(shape)
        
        // 合并原始形状和外扩形状
        def area = new java.awt.geom.Area(shape)
        area.add(new java.awt.geom.Area(expandedShape))
        
        def expandedROI = RoiTools.getShapeROI(area, roi.getImagePlane())
        
        // 2. 约束在组织区域内
        def constrainedROI = RoiTools.intersection(expandedROI, parentTissue.getROI())
        
        // 3. 扣减原始肿瘤区域（只保留环形部分）
        def rimROI = RoiTools.combineROIs(constrainedROI, roi, RoiTools.CombineOp.SUBTRACT)
        
        if (rimROI != null && !rimROI.isEmpty()) {
            // 创建环形区域注释
            def rimAnnotation = PathObjects.createAnnotationObject(
                rimROI, 
                getPathClass("Tumor_rim"),
                tumor.getMeasurementList()
            )
            resultAnnotations.add(rimAnnotation)
            println("成功为肿瘤创建外扩环形区域，外扩距离: ${expandMicrons}微米")
        }
    } catch (Exception e) {
        println("处理肿瘤区域时出错: " + e)
        e.printStackTrace()
    }
}

// 添加所有生成的环形区域
addObjects(resultAnnotations)

// 选择原始肿瘤和新生成的环形区域
selectObjects(originalTumors + resultAnnotations)

println("操作完成！成功为 ${resultAnnotations.size()}/${originalTumors.size()} 个肿瘤创建了外扩环形区域")