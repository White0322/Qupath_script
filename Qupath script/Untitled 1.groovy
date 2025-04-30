import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Stage
import javafx.stage.Window
import javafx.collections.FXCollections
import qupath.lib.gui.QuPathGUI
import qupath.lib.io.GsonTools
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.projects.Project

// 获取 QuPathGUI 实例（从 binding 或全局获取）
def qupath = binding.hasVariable('qupath') ? binding.getVariable('qupath') : QuPathGUI.getInstance()
if (!qupath) {
    println "无法获取 QuPathGUI 实例，请确保在 QuPath 环境中运行！"
    return
}

// 获取当前项目（优先使用 binding 中的 project）
def project = null
if (binding.hasVariable('project')) {
    project = binding.getVariable('project') as Project
} else if (!binding.hasVariable('imageData')) { // 单独运行时尝试获取项目
    project = qupath.getProject()
    if (!project) {
        println "请先打开一个 QuPath 项目！"
        return
    }
}

// 获取项目目录（优先使用 project，如果没有则从 imageData 推导）
def projectDir = project ? project.getPath().getParent().toFile() : null
if (!projectDir && binding.hasVariable('imageData')) {
    def imageData = binding.getVariable('imageData')
    def imagePath = new File(imageData.getServer().getPath())
    def cleanPath = imagePath.toURI().toString().replace("file://", "").replace("BioFormatsImageServer:", "")
    def cleanedFile = new File(cleanPath)
    projectDir = cleanedFile.getParentFile().getParentFile() // 假设图像在 data 文件夹下，向上两级到项目根目录
}

def annotationClasses = ["All"]
def classifierFiles = []

if (projectDir) {
    def classesFile = new File(projectDir, "classifiers/classes.json")
    if (classesFile.exists()) {
        def gson = GsonTools.getInstance()
        def jsonText = classesFile.text
        def json = gson.fromJson(jsonText, Map)
        def pathClasses = json.pathClasses as List<Map>
        annotationClasses.addAll(pathClasses.collect { it.name })
        println "从 classes.json 加载分类: ${annotationClasses.size() - 1}"
    } else {
        println "未找到 classes.json 文件，使用默认分类"
        def defaultClasses = ["Positive", "Negative", "Other", "Region*", "Tumor", "Stroma", "Immune cells", "Necrosis", "TILs", "plasma cell", "DAPI"]
        annotationClasses.addAll(defaultClasses)
    }

    def classifierDir = new File(projectDir, "classifiers/pixel_classifiers")
    if (!classifierDir.exists()) classifierDir.mkdirs()
    classifierFiles = classifierDir.listFiles({ f -> f.name.endsWith('.json') } as FileFilter)?.collect { it.name } ?: []
    println "找到的像素分类器文件: ${classifierFiles}"
}

// 参数保存路径（固定为 D:/Qupath script/params/）
def paramsDir = new File("D:/Qupath script/params/")
if (!paramsDir.exists()) paramsDir.mkdirs()

// 关闭之前打开的相同窗口
def closeExistingDialogs = { currentStage ->
    Platform.runLater {
        def stages = Window.getWindows().findAll { 
            it instanceof Stage && it.isShowing() && ((Stage) it).title == "生成目标工具" && it != currentStage 
        }
        stages.each { stage ->
            ((Stage) stage).close()
            println "已关闭一个现有的生成目标工具窗口"
        }
    }
}

// 执行生成的核心逻辑
def runGeneration = { imageData, selectedClasses, targetType, classifierName, minArea, minHoleArea, options, statusLabel = null ->
    if (!imageData) {
        def message = "错误：未打开任何图像！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    if (selectedClasses.isEmpty()) {
        def message = "错误：未选择任何分类！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    // 使用 project 或从 imageData 推导项目目录
    def localProjectDir = project ? project.getPath().getParent().toFile() : null
    if (!localProjectDir) {
        def imagePath = new File(imageData.getServer().getPath())
        def cleanPath = imagePath.toURI().toString().replace("file://", "").replace("BioFormatsImageServer:", "")
        def cleanedFile = new File(cleanPath)
        localProjectDir = cleanedFile.getParentFile().getParentFile() // 假设图像在 data 文件夹下，向上两级到项目根目录
    }

    def classifierDir = new File(localProjectDir, "classifiers/pixel_classifiers")
    if (!classifierDir.exists()) {
        def message = "错误：分类器目录 ${classifierDir.absolutePath} 不存在！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    def classifierFilePath = new File(classifierDir, classifierName + ".json")
    if (!classifierFilePath.exists()) {
        def message = "错误：像素分类器文件 ${classifierName}.json 不存在于 ${classifierDir.absolutePath}！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    // 使用 QuPath Viewer 设置图像数据
    if (qupath && qupath.getViewer()) {
        qupath.getViewer().setImageData(imageData)
    } else {
        println "错误：无法访问 QuPath Viewer，上下文可能丢失！"
        return false
    }

    // 选择对象
    selectObjects { it.getPathClass() in selectedClasses.collect { PathClass.fromString(it) } }
    
    def objectsBefore = imageData.getHierarchy().getObjects(null, null).size()
    if (targetType == "Annotation") {
        createAnnotationsFromPixelClassifier(classifierName, minArea, minHoleArea, *options)
    } else {
        createDetectionsFromPixelClassifier(classifierName, minArea, minHoleArea, *options)
    }
    def objectsAfter = imageData.getHierarchy().getObjects(null, null).size()

    def selectedNames = selectedClasses.join(", ")
    def message = "已生成 ${targetType} (分类: ${selectedNames}) 在图像 ${imageData.getServer().getMetadata().getName()}, 对象数变化: ${objectsBefore} -> ${objectsAfter}"
    if (statusLabel) {
        statusLabel.setText(message)
        statusLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    }
    println message
    if (qupath && qupath.getViewer()) {
        qupath.getViewer().repaint() // 更新 Viewer
    }
    return true
}

// 获取当前脚本名（使用堆栈跟踪）
def getCurrentScriptName() {
    def stackTrace = new Throwable().getStackTrace()
    def scriptPath = stackTrace.find { it.fileName?.endsWith(".groovy") }?.fileName
    if (scriptPath) {
        return new File(scriptPath).getName().replace('.groovy', '')
    } else {
        println "无法获取当前脚本名称，使用默认值"
        return "generate_targets"
    }
}

// 创建弹窗界面
def createAndShowDialog = { imageData ->
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

    confirmButton.setOnAction {
        def selectedClasses = checkBoxes.findAll { it.isSelected() }.collect { it.getText() }
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
        runGeneration(imageData, selectedClasses, targetType, classifierName, minArea, minHoleArea, options, statusLabel)
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
            selectNew: selectNew
        ]
        def userInput = nameField.text.trim()
        if (!userInput) userInput = "default"
        def scriptName = getCurrentScriptName()
        def fileName = "${scriptName}_${userInput}.json"
        def settingsFile = new File(paramsDir, fileName)
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
    stage.setScene(new Scene(root, 450, 600))
    stage.setResizable(true)

    def defaultParamsFile = new File(paramsDir, "${getCurrentScriptName()}_default.json")
    if (defaultParamsFile.exists()) {
        def gson = GsonTools.getInstance()
        def settings = gson.fromJson(defaultParamsFile.text, Map)
        settings.selectedClasses.each { className ->
            checkBoxes.find { it.text == className }?.selected = true
        }
        annoButton.selected = settings.targetType == "Annotation"
        detButton.selected = settings.targetType == "Detection"
        if (classifierFiles.contains(settings.classifier)) classifierCombo.value = settings.classifier
        minAreaField.text = settings.minArea
        minHoleField.text = settings.minHole
        splitCheck.selected = settings.split
        deleteCheck.selected = settings.delete
        includeIgnoredCheck.selected = settings.includeIgnored
        selectNewCheck.selected = settings.selectNew
    }

    stage.show()
    closeExistingDialogs(stage)
}

// 批量运行逻辑
def runBatch = { imageData, paramsFile ->
    println "批量运行开始，参数文件: ${paramsFile.absolutePath}"
    def gson = GsonTools.getInstance()
    def settings = gson.fromJson(paramsFile.text, Map)
    def selectedClasses = settings.selectedClasses
    def targetType = settings.targetType
    def classifierName = settings.classifier.endsWith(".json") ? settings.classifier.substring(0, settings.classifier.length() - 5) : settings.classifier
    def minArea = settings.minArea.toDouble()
    def minHole = settings.minHole.toDouble()
    def options = []
    if (settings.split) options << "SPLIT"
    if (settings.delete) options << "DELETE_EXISTING"
    if (settings.includeIgnored) options << "INCLUDE_IGNORED"
    if (settings.selectNew) options << "SELECT_NEW"

    println "加载参数 - targetType: ${targetType}, classifier: ${classifierName}, minArea: ${minArea}, minHole: ${minHole}, options: ${options}"
    runGeneration(imageData, selectedClasses, targetType, classifierName, minArea, minHole, options)
}

// 主逻辑：检查是否从管理器调用
if (binding.hasVariable('paramFile') && binding.hasVariable('imageData')) { // 从脚本管理器调用
    def paramFile = binding.getVariable('paramFile') as File
    def imageData = binding.getVariable('imageData')
    println "从管理器调用，处理图像: ${imageData.getServer().getMetadata().getName()}"
    runBatch(imageData, paramFile)
} else { // 单独运行
    def imageData = qupath.getViewer().getImageData() ?: null
    Platform.runLater {
        createAndShowDialog(imageData)
    }
}