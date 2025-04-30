// 获取当前图像数据
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// 获取所有注释对象
def annotations = hierarchy.getAnnotationObjects()

// 如果没有注释，提示并退出
if (annotations.isEmpty()) {
    print "未找到任何注释对象！"
    return
}

// 清空当前选择
hierarchy.getSelectionModel().clearSelection()

// 将注释重新插入到层次结构中
hierarchy.insertPathObjects(annotations)

// 刷新显示
hierarchy.fireHierarchyChangedEvent(this)
print "已将 ${annotations.size()} 个注释对象重新插入到层次结构中"