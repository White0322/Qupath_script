// 导入 JTS 几何运算库
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Envelope

// ----------------------- 可调整参数 -----------------------
// 交集面积超过较小注释面积的 20% 就删除（0.0 = 只要有重叠就删除，1.0 = 完全重叠才删除）
double overlapThreshold = 0.1  

// 交集面积大于 500 μm² 就删除（0 = 只要有重叠就删除,无限大=Double.MAX_VALUE）
double absoluteThreshold = Double.MAX_VALUE 

// ---------------------------------------------------------

// 获取所有注释对象
def annotations = getAnnotationObjects()
def toRemove = new HashSet()

// 预处理所有注释对象的几何信息
def annotationData = annotations.collect { ann ->
    def roi = ann.getROI()
    def geom = roi?.getGeometry()  // 获取 JTS Geometry 对象
    def area = (geom != null) ? geom.getArea() : 0.0
    def envelope = (geom != null) ? geom.getEnvelopeInternal() : null
    [annotation: ann, geom: geom, area: area, envelope: envelope]
}

// 进行两两比较，判断重叠情况
for (int i = 0; i < annotationData.size(); i++) {
    def dataA = annotationData[i]
    if (dataA.geom == null || dataA.area == 0) continue
    for (int j = i + 1; j < annotationData.size(); j++) {
        def dataB = annotationData[j]
        if (dataB.geom == null || dataB.area == 0) continue

        // 先用包围盒检查，如果包围盒不相交，则直接跳过
        if (!dataA.envelope.intersects(dataB.envelope)) continue

        // 计算精确交集
        def inter = dataA.geom.intersection(dataB.geom)
        if (!inter.isEmpty()) {
            def interArea = inter.getArea()
            def minArea = Math.min(dataA.area, dataB.area)

            // 判断是否满足删除条件（比例阈值 OR 绝对面积阈值）
            boolean shouldRemove = (interArea / minArea > overlapThreshold) || (interArea > absoluteThreshold)
            if (shouldRemove) {
                if (dataA.area < dataB.area) {
                    toRemove.add(dataA.annotation)
                } else {
                    toRemove.add(dataB.annotation)
                }
            }
        }
    }
}

// 删除重叠的注释对象
toRemove.each { ann -> removeObject(ann, true) }
fireHierarchyUpdate()

println "已删除 ${toRemove.size()} 个重叠注释。"
