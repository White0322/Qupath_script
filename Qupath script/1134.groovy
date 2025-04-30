import qupath.lib.gui.QuPathGUI
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.io.GsonTools
import qupath.lib.scripting.QP
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Stage
import javafx.stage.Window
import javafx.collections.FXCollections

// 获取 QuPathGUI 实例（从 binding 或全局获取）
def qupath = binding.hasVariable('qupath') ? binding.getVariable('qupath') : QuPathGUI.getInstance()
if (!qupath && !binding.hasVariable('imageData')) {
    println "错误：无法获取 QuPathGUI 实例，且未提供 imageData，请确保在 QuPath 环境中运行！"
    return
}

// 获取项目目录（优先使用 project，如果没有则从 imageData 推导）
def getProjectDir = { imageData, project ->
    if (project) {
        return project.getPath().getParent().toFile()
    } else if (imageData) {
        def imagePath = new File(imageData.getServer().getPath())
        def cleanPath = imagePath.toURI().toString().replace("file://", "").replace("BioFormatsImageServer:", "")
        return new File(cleanPath).getParentFile().getParentFile() // 假设图像在 data 文件夹下，向上两级到项目根目录
    }
    println "警告：无法推导项目目录，使用默认路径！"
    return new File(System.getProperty("user.dir")) // 使用当前工作目录作为后备
}

// 加载像素分类器
def loadPixelClassifier = { classifierName, projectDir ->
    // 确保 classifierName 包含正确的扩展名
    def classifierFileName = classifierName.endsWith(".json") ? classifierName : "${classifierName}.json"
    def classifierFile = new File(projectDir, "classifiers/pixel_classifiers/${classifierFileName}")
    if (!classifierFile.exists()) {
        println "错误：像素分类器文件 ${classifierFileName} 不存在于 ${classifierFile.absolutePath}！"
        // 尝试在图像所在目录下查找（如果项目目录推导失败）
        def imageDir = new File(projectDir, "data").listFiles()?.find { it.name.endsWith(".qptiff") || it.name.endsWith(".tiff") }?.parentFile
        if (imageDir) {
            def altClassifierFile = new File(imageDir, "classifiers/pixel_classifiers/${classifierFileName}")
            if (altClassifierFile.exists()) {
                println "找到备用分类器文件：${altClassifierFile.absolutePath}"
                classifierFile = altClassifierFile
            } else {
                println "错误：备用路径下也未找到分类器文件！"
                return null
            }
        } else {
            return null
        }
    }
    try {
        return QP.loadPixelClassifier(classifierFile.absolutePath)
    } catch (Exception e) {
        println "错误：加载像素分类器失败 - ${e.message}"
        return null
    }
}

// 获取指定区域（Annotation）的 ROI
def getRegionROI = { hierarchy, regionLabel ->
    if (!regionLabel || regionLabel == "整个图像") {
        return null // 返回 null 表示全片
    }
    def annotations = hierarchy.getAnnotationObjects().findAll { 
        it.getPathClass()?.toString() == regionLabel 
    }
    if (annotations.isEmpty()) {
        println "警告：未找到标签为 ${regionLabel} 的注释区域，使用整个图像！"
        return null
    }
    // 确保返回第一个匹配的 ROI
    return annotations[0].getROI()
}

// 执行生成的核心逻辑
def runGeneration = { imageData, selectedClasses, targetType, classifierName, minArea, minHoleArea, options, regionLabel = null ->
    if (!imageData) {
        println "错误：未提供任何图像数据！"
        return false
    }

    // 检查 selectedClasses 是否为空
    if (selectedClasses.isEmpty()) {
        println "错误：未选择任何分类！"
        return false
    }

    // 获取层级结构
    def hierarchy = imageData.getHierarchy()
    if (!hierarchy) {
        println "错误：图像层级结构无效！"
        return false
    }

    // 获取项目目录
    def project = binding.hasVariable('project') ? binding.getVariable('project') : qupath?.getProject()
    def projectDir = getProjectDir(imageData, project)

    // 加载像素分类器
    def classifier = loadPixelClassifier(classifierName, projectDir)
    if (!classifier) {
        return false
    }

    // 获取指定区域的 ROI（如果有）
    def regionROI = getRegionROI(hierarchy, regionLabel)

    // 检查现有的对象，避免重复生成
    def existingObjects = hierarchy.getObjects(null, null).findAll { 
        it.getPathClass() in selectedClasses.collect { PathClass.fromString(it) } 
    }
    if (!options.contains("DELETE_EXISTING")) {
        hierarchy.removeObjects(existingObjects, true) // 清除现有匹配的对象
    }

    // 生成目标（限制在指定区域或整张图像）
    def objectsBefore = hierarchy.getObjects(null, null).size()
    try {
        if (targetType == "Annotation") {
            def annotations = regionROI ? 
                QP.createAnnotationsFromPixelClassifier(classifier, regionROI, minArea, minHoleArea, *options) : 
                QP.createAnnotationsFromPixelClassifier(classifier, minArea, minHoleArea, *options)
            if (annotations == null || annotations.isEmpty()) {
                println "警告：未生成任何 Annotation 对象！可能是分类器或图像问题。"
                return false
            }
            hierarchy.addPathObjects(annotations)
        } else {
            def detections = regionROI ? 
                QP.createDetectionsFromPixelClassifier(classifier, regionROI, minArea, minHoleArea, *options) : 
                QP.createDetectionsFromPixelClassifier(classifier, minArea, minHoleArea, *options)
            if (detections == null || detections.isEmpty()) {
                println "警告：未生成任何 Detection 对象！可能是分类器或图像问题。"
                return false
            }
            hierarchy.addPathObjects(detections)
        }
    } catch (Exception e) {
        println "错误：生成目标时失败 - ${e.message}"
        return false
    }

    def objectsAfter = hierarchy.getObjects(null, null).size()

    // 触发层级变化事件，确保对象更新
    hierarchy.fireHierarchyChangedEvent(hierarchy)

    // 输出结果
    def selectedNames = selectedClasses.join(", ")
    def regionInfo = regionLabel ? " (区域: ${regionLabel})" : " (全片)"
    println "已生成 ${targetType} (分类: ${selectedNames})${regionInfo} 在图像 ${imageData.getServer().getMetadata().getName()}, 对象数变化: ${objectsBefore} -> ${objectsAfter}"
    return true
}

// 批量运行逻辑
def runBatch = { imageData, paramFile ->
    println "批量运行开始，参数文件: ${paramFile.absolutePath}"
    def gson = GsonTools.getInstance()
    def settings = gson.fromJson(paramFile.text, Map)
    
    def selectedClasses = settings.selectedClasses as List<String>
    def targetType = settings.targetType as String
    def classifierName = settings.classifier.endsWith(".json") ? settings.classifier.substring(0, settings.classifier.length() - 5) : settings.classifier as String
    def minArea = settings.minArea.toDouble()
    def minHoleArea = settings.minHole.toDouble()
    def options = []
    if (settings.split) options << "SPLIT"
    if (settings.delete) options << "DELETE_EXISTING"
    if (settings.includeIgnored) options << "INCLUDE_IGNORED"
    if (settings.selectNew) options << "SELECT_NEW"
    
    // 添加区域标签支持（从参数文件中读取）
    def regionLabel = settings.regionLabel ?: "整个图像"

    println "加载参数 - targetType: ${targetType}, classifier: ${classifierName}, minArea: ${minArea}, minHoleArea: ${minHoleArea}, options: ${options}, regionLabel: ${regionLabel}"
    def success = runGeneration(imageData, selectedClasses, targetType, classifierName, minArea, minHoleArea, options, regionLabel)
    if (!success) {
        println "批量运行失败：目标未生成，请检查参数、分类器文件或图像数据！"
    }
    return success
}

// 创建弹窗界面（仅用于单独运行）
def createAndShowDialog = { imageData ->
    // 提前获取 hierarchy，确保在闭包中可用
    def hierarchy = imageData?.getHierarchy()
    if (!hierarchy) {
        println "错误：图像层级结构无效！"
        return
    }

    def root = new VBox(15)
    root.setPadding(new Insets(15))
    root.setStyle("-fx-background-color: #ffffff;")

    def pane = new GridPane()
    pane.setHgap(10)
    pane.setVgap(10)
    pane.setPadding(new Insets(10))
    pane.setStyle("-fx-background-color: #ffffff;")

    def typeLabel = new Label("生成目标类型：")
    typeLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(typeLabel, 0, 1)

    def toggleGroup = new ToggleGroup()
    def annoButton = new ToggleButton("Annotation")
    def detButton = new ToggleButton("Detection")
    annoButton.setToggleGroup(toggleGroup)
    detButton.setToggleGroup(toggleGroup)
    annoButton.setSelected(true)

    def toggleButtonStyle = "-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-text-fill: #333333;"
    def toggleButtonHoverStyle = "-fx-background-color: #e0e0e0; -fx-border-color: #999999;"
    def toggleButtonSelectedStyle = "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-border-color: #45a049; -fx-font-weight: bold;"

    annoButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
    detButton.setStyle(toggleButtonStyle)

    annoButton.setOnMouseEntered { if (!annoButton.isSelected()) annoButton.setStyle(toggleButtonStyle + toggleButtonHoverStyle) }
    annoButton.setOnMouseExited { if (!annoButton.isSelected()) annoButton.setStyle(toggleButtonStyle) }
    detButton.setOnMouseEntered { if (!detButton.isSelected()) detButton.setStyle(toggleButtonStyle + toggleButtonHoverStyle) }
    detButton.setOnMouseExited { if (!detButton.isSelected()) detButton.setStyle(toggleButtonStyle) }

    toggleGroup.selectedToggleProperty().addListener({ observable, oldToggle, newToggle ->
        if (newToggle == annoButton) {
            annoButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
            detButton.setStyle(toggleButtonStyle)
        } else if (newToggle == detButton) {
            detButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
            annoButton.setStyle(toggleButtonStyle)
        }
    } as javafx.beans.value.ChangeListener)

    pane.add(annoButton, 1, 1)
    pane.add(detButton, 1, 2)

    def classLabel = new Label("选择标签：")
    classLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(classLabel, 0, 3)

    def annotationClasses = ["All", "Positive", "Negative", "Tumor", "Stroma", "Immune cells", "Necrosis", "Other", "Region*"]
    def vbox = new VBox(5)
    def checkBoxes = annotationClasses.collect { className ->
        def checkBox = new CheckBox(className)
        checkBox.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
        checkBox
    }
    vbox.getChildren().addAll(checkBoxes)

    def scrollPane = new ScrollPane(vbox)
    scrollPane.setFitToWidth(true)
    scrollPane.setPrefHeight(150)
    scrollPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px;")
    pane.add(scrollPane, 1, 3)

    def classifierLabel = new Label("选择分类器：")
    classifierLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(classifierLabel, 0, 4)
    def projectDir = getProjectDir(imageData, qupath?.getProject())
    def classifierFiles = projectDir ? new File(projectDir, "classifiers/pixel_classifiers").listFiles({ f -> f.name.endsWith('.json') } as FileFilter)?.collect { it.name } ?: [] : []
    def classifierCombo = new ComboBox(FXCollections.observableArrayList(classifierFiles))
    classifierCombo.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    classifierCombo.setPrefWidth(250)
    if (!classifierFiles.isEmpty()) classifierCombo.setValue(classifierFiles[0])
    pane.add(classifierCombo, 1, 4)

    def minAreaLabel = new Label("最小面积过滤：")
    minAreaLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(minAreaLabel, 0, 5)
    def minAreaField = new TextField("0.0")
    minAreaField.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    minAreaField.setPrefWidth(100)
    pane.add(minAreaField, 1, 5)

    def minHoleLabel = new Label("最小空洞过滤：")
    minHoleLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(minHoleLabel, 0, 6)
    def minHoleField = new TextField("0.0")
    minHoleField.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    minHoleField.setPrefWidth(100)
    pane.add(minHoleField, 1, 6)

    def optionsLabel = new Label("生成选项：")
    optionsLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(optionsLabel, 0, 7)
    def optionsVBox = new VBox(5)
    def splitCheck = new CheckBox("分割生成目标 (SPLIT)")
    def deleteCheck = new CheckBox("删除现有目标 (DELETE_EXISTING)")
    def includeIgnoredCheck = new CheckBox("包含忽视的目标 (INCLUDE_IGNORED)")
    def selectNewCheck = new CheckBox("设置新选择对象 (SELECT_NEW)")
    [splitCheck, deleteCheck, includeIgnoredCheck, selectNewCheck].each {
        it.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
    }
    optionsVBox.getChildren().addAll(splitCheck, deleteCheck, includeIgnoredCheck, selectNewCheck)
    pane.add(optionsVBox, 1, 7)

    // 添加区域标签选择
    def regionLabelChoice = new Label("生成区域标签：")
    regionLabelChoice.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(regionLabelChoice, 0, 8)
    def allPathClasses = (hierarchy?.getAnnotationObjects()?.collect { it.getPathClass()?.toString() }?.findAll { it } ?: []) + ["整个图像"]
    def regionCombo = new ComboBox(FXCollections.observableArrayList(allPathClasses))
    regionCombo.setValue("整个图像")
    regionCombo.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    regionCombo.setPrefWidth(250)
    pane.add(regionCombo, 1, 8)

    def savePane = new GridPane()
    savePane.setHgap(10)
    savePane.setVgap(10)
    savePane.setPadding(new Insets(10))
    def nameLabel = new Label("参数文件名：")
    def nameField = new TextField("生成目标参数")
    savePane.add(nameLabel, 0, 0)
    savePane.add(nameField, 1, 0)

    def buttonBox = new HBox(20)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(10))
    buttonBox.setStyle("-fx-background-color: #ffffff;")

    def confirmButton = new Button("确认生成")
    confirmButton.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    confirmButton.setOnMouseEntered { confirmButton.setStyle("-fx-background-color: linear-gradient(to bottom, #45a049, #4CAF50); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    confirmButton.setOnMouseExited { confirmButton.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

    def saveSettingsButton = new Button("保存参数")
    saveSettingsButton.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    saveSettingsButton.setOnMouseEntered { saveSettingsButton.setStyle("-fx-background-color: linear-gradient(to bottom, #1976D2, #2196F3); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    saveSettingsButton.setOnMouseExited { saveSettingsButton.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

    def closeButton = new Button("关闭")
    closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    closeButton.setOnMouseEntered { closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #d32f2f, #f44336); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    closeButton.setOnMouseExited { closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

    buttonBox.getChildren().addAll(confirmButton, saveSettingsButton, closeButton)

    def statusLabel = new Label(imageData ? "就绪" : "未打开图像，请打开图像后再操作")
    statusLabel.setStyle("-fx-text-fill: #B74747; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: #f0f0f0; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    statusLabel.setMaxWidth(Double.MAX_VALUE)
    statusLabel.setWrapText(true)
    statusLabel.setPrefHeight(100)

    // 定义 regionLabel 一次，在函数作用域中
    def regionLabelValue = "整个图像" // 默认值
    confirmButton.setOnAction {
        def selectedClasses = checkBoxes.findAll { it.isSelected() }.collect { it.getText() }
        if (selectedClasses.isEmpty()) {
            statusLabel.setText("错误：请至少选择一个分类以生成！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }
        def targetType = annoButton.isSelected() ? "Annotation" : "Detection"
        def classifierFile = classifierCombo.getValue()
        def minAreaText = minAreaField.getText()
        def minHoleText = minHoleField.getText()
        def doSplit = splitCheck.isSelected()
        def doDelete = deleteCheck.isSelected()
        def includeIgnored = includeIgnoredCheck.isSelected()
        def selectNew = selectNewCheck.isSelected()
        def options = []
        if (doSplit) options << "SPLIT"
        if (doDelete) options << "DELETE_EXISTING"
        if (includeIgnored) options << "INCLUDE_IGNORED"
        if (selectNew) options << "SELECT_NEW"

        def minArea, minHoleArea
        try {
            minArea = minAreaText.toDouble()
            minHoleArea = minHoleText.toDouble()
        } catch (Exception e) {
            statusLabel.setText("错误：最小面积和空洞过滤必须为数值！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }

        def classifierName = classifierFile.endsWith(".json") ? classifierFile.substring(0, classifierFile.length() - 5) : classifierFile
        regionLabelValue = regionCombo.getValue() // 更新 regionLabelValue
        runGeneration(imageData, selectedClasses, targetType, classifierName, minArea, minHoleArea, options, regionLabelValue)
    }

    saveSettingsButton.setOnAction {
        def selectedClasses = checkBoxes.findAll { it.isSelected() }.collect { it.getText() }
        if (selectedClasses.isEmpty()) {
            statusLabel.setText("错误：请至少选择一个分类以保存！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }
        def targetType = annoButton.isSelected() ? "Annotation" : "Detection"
        def classifierFile = classifierCombo.getValue()
        def minAreaText = minAreaField.getText()
        def minHoleText = minHoleField.getText()
        def doSplit = splitCheck.isSelected()
        def doDelete = deleteCheck.isSelected()
        def includeIgnored = includeIgnoredCheck.isSelected()
        def selectNew = selectNewCheck.isSelected()

        def settings = [
            selectedClasses: selectedClasses,
            targetType: targetType,
            classifier: classifierFile,
            minArea: minAreaText,
            minHole: minHoleText,
            split: doSplit,
            delete: doDelete,
            includeIgnored: includeIgnored,
            selectNew: selectNew,
            regionLabel: regionLabelValue // 使用统一的值
        ]
        def userInput = nameField.text.trim()
        if (!userInput) userInput = "default"
        def scriptName = "generate_targets" // 固定脚本名
        def fileName = "${scriptName}_${userInput}.json"
        def settingsFile = new File("D:/Qupath script/params", fileName)
        def gson = GsonTools.getInstance()
        settingsFile.text = gson.toJson(settings)
        statusLabel.setText("参数已保存到 ${settingsFile.getAbsolutePath()}")
        statusLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
        println "保存参数文件: ${settingsFile.absolutePath}"
    }

    closeButton.setOnAction {
        closeButton.scene.window.hide()
    }

    root.getChildren().addAll(pane, savePane, buttonBox, statusLabel)

    def stage = new Stage()
    stage.setTitle("生成目标工具")
    stage.setScene(new Scene(root, 450, 650)) // 增加高度以适应新控件
    stage.setResizable(true)

    // 加载默认参数（如果存在）
    def defaultParamsFile = new File("D:/Qupath script/params", "generate_targets_default.json")
    if (defaultParamsFile.exists()) {
        def gson = GsonTools.getInstance()
        def settings = gson.fromJson(defaultParamsFile.text, Map)
        settings.selectedClasses.each { className ->
            checkBoxes.find { it.text == className }?.setSelected(true)
        }
        annoButton.setSelected(settings.targetType == "Annotation")
        detButton.setSelected(settings.targetType == "Detection")
        if (classifierFiles.contains(settings.classifier)) classifierCombo.setValue(settings.classifier)
        minAreaField.setText(settings.minArea.toString())
        minHoleField.setText(settings.minHole.toString())
        splitCheck.setSelected(settings.split)
        deleteCheck.setSelected(settings.delete)
        includeIgnoredCheck.setSelected(settings.includeIgnored)
        selectNewCheck.setSelected(settings.selectNew)
        if (settings.regionLabel && (settings.regionLabel in (["整个图像"] + (hierarchy?.getAnnotationObjects()?.collect { it.getPathClass()?.toString() }?.findAll { it } ?: [])))) {
            regionCombo.setValue(settings.regionLabel)
            regionLabelValue = settings.regionLabel
        }
    }

    stage.show()
    // 关闭之前打开的相同窗口
    def closeExistingDialogs = { currentStage ->
        Platform.runLater {
            def stages = Window.getWindows().findAll { 
                it instanceof Stage && it.isShowing() && ((Stage) it).title == "生成目标工具" && it != currentStage 
            }
            stages.each { currentStageItem ->
                ((Stage) currentStageItem).close()
                println "已关闭一个现有的生成目标工具窗口"
            }
        }
    }
    closeExistingDialogs(stage)
}

// 主逻辑：支持单独运行和批量运行
def project = qupath?.getProject()
def projectDir = project ? project.getPath().getParent().toFile() : null
def paramsDir = new File("D:/Qupath script/params/")
if (!paramsDir.exists()) paramsDir.mkdirs()

if (binding.hasVariable('paramFile') && binding.hasVariable('imageData')) { // 从脚本管理器调用（批量运行）
    def paramFile = binding.getVariable('paramFile') as File
    def imageData = binding.getVariable('imageData')
    println "从管理器调用，处理图像: ${imageData.getServer().getMetadata().getName()}"
    runBatch(imageData, paramFile)
} else { // 单独运行
    def imageData = qupath?.getViewer()?.getImageData() ?: null
    if (!imageData) {
        println "警告：未打开图像，请先加载图像后再运行！"
        return
    }
    Platform.runLater {
        createAndShowDialog(imageData)
    }
}