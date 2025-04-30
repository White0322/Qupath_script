import qupath.lib.gui.QuPathGUI
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import javafx.application.Platform
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.stage.Stage
import javafx.scene.Scene
import javafx.stage.Window
import javafx.beans.value.ChangeListener
import qupath.lib.io.GsonTools

// 获取 QuPathGUI 实例（从 binding 或全局获取）
def qupath = binding.hasVariable('qupath') ? binding.getVariable('qupath') : QuPathGUI.getInstance()

// 定义默认分类
def defaultClasses = [
    PathClass.getInstance("Positive"),
    PathClass.getInstance("Negative"),
    PathClass.getInstance("Region*"),
    PathClass.getInstance("Other"),
    PathClass.getInstance("Tumor"),
    PathClass.getInstance("Stroma")
]

// 获取当前图像中所有 Annotation 和 Detection 的分类
def fetchClasses = { imageData ->
    if (!imageData) {
        println "未打开图像，返回默认分类"
        return defaultClasses  // 如果 imageData 为 null，返回默认分类
    }
    def annotations = imageData.getHierarchy().getAnnotationObjects()
    def detections = imageData.getHierarchy().getDetectionObjects()
    def existingClasses = (annotations + detections).collect { it.getPathClass() }.findAll { it != null }.unique()
    (defaultClasses + existingClasses).unique { it.toString() }
}

// 关闭之前打开的相同窗口
def closeExistingDialogs = { currentStage ->
    Platform.runLater {
        def stages = Window.getWindows().findAll { 
            it instanceof Stage && it.isShowing() && ((Stage) it).title == "对象转换工具" && it != currentStage 
        }
        stages.each { stage ->
            ((Stage) stage).close()
            println "已关闭一个现有的对象转换工具窗口"
        }
    }
}

// 执行转换的核心逻辑
def runConversion = { imageData, selectedClasses, isAnnoToDet, statusLabel = null ->
    def sourceObjects = isAnnoToDet ? imageData.getHierarchy().getAnnotationObjects() : imageData.getHierarchy().getDetectionObjects()
    def sourceType = isAnnoToDet ? "Annotation" : "Detection"
    def targetType = isAnnoToDet ? "Detection" : "Annotation"

    if (sourceObjects.isEmpty()) {
        def message = "错误：当前图像中没有 ${sourceType} 对象！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    def subset = sourceObjects.findAll { obj ->
        obj.getROI() && selectedClasses.contains(obj.getPathClass())
    }

    if (subset.isEmpty()) {
        def missingClasses = selectedClasses.collect { it.toString() }.join(", ")
        def message = "提示：未找到分类为 '${missingClasses}' 的 ${sourceType}！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    def newObjects = subset.collect {
        if (isAnnoToDet) {
            PathObjects.createDetectionObject(it.getROI(), it.getPathClass(), it.getMeasurementList())
        } else {
            PathObjects.createAnnotationObject(it.getROI(), it.getPathClass(), it.getMeasurementList())
        }
    }
    imageData.getHierarchy().removeObjects(subset, true)
    imageData.getHierarchy().addObjects(newObjects)

    def selectedNames = selectedClasses.collect { it.toString() }.join(", ")
    def message = "已将 ${subset.size()} 个 ${sourceType} (分类: ${selectedNames}) 转换为 ${targetType} 在图像 ${imageData.getServer().getMetadata().getName()}"
    if (statusLabel) {
        statusLabel.setText(message)
        statusLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    }
    println message
    return true
}

// 获取当前脚本名
def getCurrentScriptName() {
    def stackTrace = new Throwable().getStackTrace()
    def scriptPath = stackTrace.find { it.fileName?.endsWith(".groovy") }?.fileName
    if (scriptPath) {
        return new File(scriptPath).getName().replace('.groovy', '')
    } else {
        println "无法获取当前脚本名称"
        return "default"
    }
}

// 创建弹窗界面
def createAndShowDialog = { paramsDir, imageData ->
    def root = new VBox(15)
    root.setPadding(new Insets(15))
    root.setStyle("-fx-background-color: #ffffff;")

    def pane = new GridPane()
    pane.setHgap(10)
    pane.setVgap(10)
    pane.setPadding(new Insets(10))
    pane.setStyle("-fx-background-color: #ffffff;")

    def typeLabel = new Label("转换类型：")
    typeLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(typeLabel, 0, 1)

    def toggleGroup = new ToggleGroup()
    def annoToDetButton = new ToggleButton("Annotation -> Detection")
    def detToAnnoButton = new ToggleButton("Detection -> Annotation")
    annoToDetButton.setToggleGroup(toggleGroup)
    detToAnnoButton.setToggleGroup(toggleGroup)
    annoToDetButton.setSelected(true)

    def toggleButtonStyle = "-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-text-fill: #333333;"
    def toggleButtonHoverStyle = "-fx-background-color: #e0e0e0; -fx-border-color: #999999;"
    def toggleButtonSelectedStyle = "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-border-color: #45a049; -fx-font-weight: bold;"

    annoToDetButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
    detToAnnoButton.setStyle(toggleButtonStyle)

    annoToDetButton.setOnMouseEntered { if (!annoToDetButton.isSelected()) annoToDetButton.setStyle(toggleButtonStyle + toggleButtonHoverStyle) }
    annoToDetButton.setOnMouseExited { if (!annoToDetButton.isSelected()) annoToDetButton.setStyle(toggleButtonStyle) }
    detToAnnoButton.setOnMouseEntered { if (!detToAnnoButton.isSelected()) detToAnnoButton.setStyle(toggleButtonStyle + toggleButtonHoverStyle) }
    detToAnnoButton.setOnMouseExited { if (!detToAnnoButton.isSelected()) detToAnnoButton.setStyle(toggleButtonStyle) }

    toggleGroup.selectedToggleProperty().addListener({ observable, oldToggle, newToggle ->
        if (newToggle == annoToDetButton) {
            annoToDetButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
            detToAnnoButton.setStyle(toggleButtonStyle)
        } else if (newToggle == detToAnnoButton) {
            detToAnnoButton.setStyle(toggleButtonStyle + toggleButtonSelectedStyle)
            annoToDetButton.setStyle(toggleButtonStyle)
        }
    } as ChangeListener)

    pane.add(annoToDetButton, 1, 1)
    pane.add(detToAnnoButton, 1, 2)

    def classLabel = new Label("选择标签：")
    classLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    pane.add(classLabel, 0, 3)

    def allClasses = fetchClasses(imageData)
    def vbox = new VBox(5)
    def checkBoxes = allClasses.collect { pathClass ->
        def checkBox = new CheckBox(pathClass.toString())
        checkBox.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
        checkBox
    }
    vbox.getChildren().addAll(checkBoxes)

    def scrollPane = new ScrollPane(vbox)
    scrollPane.setFitToWidth(true)
    scrollPane.setPrefHeight(150)
    scrollPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px;")
    pane.add(scrollPane, 1, 3)

    def savePane = new GridPane()
    savePane.setHgap(10)
    savePane.setVgap(10)
    savePane.setPadding(new Insets(10))
    def nameLabel = new Label("参数文件名：")
    def nameField = new TextField("对象转换参数")
    savePane.add(nameLabel, 0, 0)
    savePane.add(nameField, 1, 0)

    def buttonBox = new HBox(20)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(10))
    buttonBox.setStyle("-fx-background-color: #ffffff;")

    def confirmButton = new Button("确认转换")
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
        def selectedClasses = checkBoxes.findAll { it.isSelected() }.collect { PathClass.fromString(it.getText()) }
        if (selectedClasses.isEmpty()) {
            statusLabel.setText("错误：请至少选择一个分类！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }

        if (!imageData) {
            statusLabel.setText("错误：未打开任何图像！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }
        def isAnnoToDet = annoToDetButton.isSelected()
        runConversion(imageData, selectedClasses, isAnnoToDet, statusLabel)
    }

    saveSettingsButton.setOnAction {
        def selectedClasses = checkBoxes.findAll { it.isSelected() }.collect { it.getText() }
        if (selectedClasses.isEmpty()) {
            statusLabel.setText("错误：请至少选择一个分类以保存！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            return
        }
        def isAnnoToDet = annoToDetButton.isSelected()
        def settings = [
            "isAnnoToDet": isAnnoToDet,
            "selectedClasses": selectedClasses
        ]
        def userInput = nameField.text.trim()
        if (!userInput) userInput = "default"
        def scriptName = getCurrentScriptName()
        def fileName = "${scriptName}_${userInput}"
        if (!fileName.endsWith(".json")) fileName += ".json"
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
    stage.setTitle("对象转换工具")
    stage.setScene(new Scene(root, 450, 500))
    stage.setResizable(true)

    stage.show()
    closeExistingDialogs(stage)
}

// 批量运行逻辑
def runBatch = { imageData, paramsFile ->
    println "批量运行开始，参数文件: ${paramsFile.absolutePath}"
    def gson = GsonTools.getInstance()
    def settings = gson.fromJson(paramsFile.text, Map)
    def selectedClasses = settings.selectedClasses.collect { PathClass.fromString(it) }
    def isAnnoToDet = settings.isAnnoToDet
    println "加载参数 - isAnnoToDet: ${isAnnoToDet}, selectedClasses: ${selectedClasses}"
    runConversion(imageData, selectedClasses, isAnnoToDet)
}

// 主逻辑：检查是否从管理器调用
def project = qupath.getProject()
def projectDir = project.getPath().toFile().getParentFile()
def paramsDir = new File(projectDir, "params")
if (!paramsDir.exists()) paramsDir.mkdirs()

if (binding.hasVariable('paramFile') && binding.hasVariable('imageData')) { // 从脚本管理器调用
    def paramFile = binding.getVariable('paramFile') as File
    def imageData = binding.getVariable('imageData')
    println "从管理器调用，处理图像: ${imageData.getServer().getMetadata().getName()}"
    runBatch(imageData, paramFile)
} else { // 单独运行
    def imageData = qupath.getViewer().getImageData() ?: null // 获取当前图像数据
    Platform.runLater {
        createAndShowDialog(paramsDir, imageData)
    }
}