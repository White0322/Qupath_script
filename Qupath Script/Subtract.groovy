// 获取当前图像数据
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// 获取所有标注对象
def annotations = hierarchy.getAnnotationObjects()

// 分离内外环标注
def exAnnotations = annotations.findAll { it.getPathClass()?.getName() == "Ex" }
def inAnnotations = annotations.findAll { it.getPathClass()?.getName() == "In" }

// 创建存储结果的列表
def wallAnnotations = []
def toRemove = []  // 新增：待删除标注集合

// 遍历所有内环标注
inAnnotations.each { inAnn ->
    def inROI = inAnn.getROI()
    def inPlane = inROI.getImagePlane()
    
    // 寻找包含该内环的外环标注
    def exAnn = exAnnotations.find { ex ->
        def exROI = ex.getROI()
        exROI.getImagePlane() == inPlane && 
        exROI.getGeometry().covers(inROI.getGeometry())
    }
    
    if (exAnn) {
        // 执行几何差集运算
        def exGeo = exAnn.getROI().getGeometry()
        def inGeo = inROI.getGeometry()
        def wallGeo = exGeo.difference(inGeo)
        
        // 创建新的血管壁标注
        if (!wallGeo.isEmpty()) {
            def wallROI = GeometryTools.geometryToROI(wallGeo, inPlane)
            def wallClass = PathClass.fromString("Wall")
            def wallAnn = PathObjects.createAnnotationObject(wallROI, wallClass)
            wallAnnotations.add(wallAnn)
            
            // 标记待删除的原始标注（修改部分）
            toRemove.addAll([exAnn, inAnn])
        }
    }
}

// 添加新标注
hierarchy.addPathObjects(wallAnnotations)

// 删除原始标注（修正部分）
if (!toRemove.isEmpty()) {
    hierarchy.removeObjects(toRemove, true)  // 第二个参数 true 表示强制删除
}

// 刷新显示
hierarchy.fireHierarchyChangedEvent(this)
print "生成 ${wallAnnotations.size()} 个血管壁区域，删除 ${toRemove.size()} 个原始标注"

// 可选：运行测量命令
runPlugin('qupath.lib.algorithms.IntensityFeaturesPlugin', '{"pixelSizeMicrons": 0.5, "region": "ROI"}')