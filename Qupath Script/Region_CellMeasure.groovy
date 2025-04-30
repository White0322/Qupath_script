// QuPath脚本：可配置的多参数细胞统计计算
// 参数配置部分 - 只需修改这部分即可适应不同需求
// ==============================================

// 1. 定义要分析的测量参数列表
def measurementParams = [
    "Cell: ARG-1 mean",  // 参数1
    "Cell: INOS mean",   // 参数2
    // 可以添加更多参数...
    // "Cell: Caspase-1 mean"
]

// 2. 定义要统计的细胞类别
def cellClasses = [
    "Arg-1",  // 类别1
    "Inos",   // 类别2
    // 可以添加更多类别...
    // "Caspase1+"
]

// 3. 配置选项
def options = [
    calculateAverages: true,  // 是否计算平均值
    calculatePercentages: true,  // 是否计算百分比
    percentageBase: "all"  // 百分比计算基准: "all"(所有细胞) 或 "classified"(已分类细胞)
]

// ==============================================
// 核心计算逻辑部分 - 无需修改
// ==============================================

// 获取当前图像数据
def imageData = getCurrentImageData()
def annotations = getAnnotationObjects()

annotations.each { annotation ->
    def cells = getCurrentHierarchy().getObjectsForROI(null, annotation.getROI())
    def ml = annotation.getMeasurementList()
    
    // 初始化统计存储
    def stats = [:]
    cellClasses.each { cls ->
        stats[cls] = [
            count: 0,
            measurements: [:].withDefault { [sum: 0, count: 0] }
        ]
    }
    
    // 统计未分类细胞
    int unclassifiedCount = 0
    int totalCells = 0
    
    // 遍历细胞进行统计
    cells.each { cell ->
        if (cell.isCell()) {
            totalCells++
            def cellClass = cell.getPathClass()?.toString()
            
            if (cellClass && cellClasses.any { it.equalsIgnoreCase(cellClass) }) {
                // 找到匹配的类别（不区分大小写）
                def matchedClass = cellClasses.find { it.equalsIgnoreCase(cellClass) }
                def classStat = stats[matchedClass]
                classStat.count++
                
                // 收集测量值
                measurementParams.each { param ->
                    def value = cell.getMeasurementList().getMeasurementValue(param)
                    if (value != null) {
                        classStat.measurements[param].sum += value
                        classStat.measurements[param].count++
                    }
                }
            } else {
                unclassifiedCount++
            }
        }
    }
    
    // 计算并存储结果
    int classifiedCount = totalCells - unclassifiedCount
    
    // 1. 存储计数结果
    cellClasses.each { cls ->
        ml.putMeasurement("${cls} Count", stats[cls].count)
    }
    ml.putMeasurement("Total Cells", totalCells)
    ml.putMeasurement("Classified Cells", classifiedCount)
    ml.putMeasurement("Unclassified Cells", unclassifiedCount)
    
    // 2. 计算并存储平均值
    if (options.calculateAverages) {
        cellClasses.each { cls ->
            measurementParams.each { param ->
                def m = stats[cls].measurements[param]
                if (m.count > 0) {
                    ml.putMeasurement("${cls} ${param} Avg", m.sum / m.count)
                }
            }
        }
    }
    
    // 3. 计算并存储百分比
    if (options.calculatePercentages) {
        def base = options.percentageBase == "classified" ? classifiedCount : totalCells
        if (base > 0) {
            cellClasses.each { cls ->
                def percentage = (stats[cls].count / base) * 100
                ml.putMeasurement("${cls} Percentage", percentage)
            }
        }
    }
    
    // 打印结果摘要
    println "Annotation: ${annotation.getName()}"
    cellClasses.each { cls ->
        println "  ${cls}: ${stats[cls].count} cells"
    }
    println "  Total: ${totalCells} cells (${classifiedCount} classified, ${unclassifiedCount} unclassified)"
}

// 刷新显示
fireHierarchyUpdate()
print "Analysis completed!"