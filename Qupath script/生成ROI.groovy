import qupath.lib.gui.QuPathGUI
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import qupath.lib.io.GsonTools
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.stage.Stage
import javafx.scene.Scene
import javafx.stage.Window
import java.util.Random

// 获取 QuPathGUI 实例
def qupath = binding.hasVariable('qupath') ? binding.getVariable('qupath') : QuPathGUI.getInstance()

// 默认参数
def defaultSettings = [
    "numROIs": 5,
    "shapeType": "rectangle",
    "width": 50.0,
    "height": 50.0,
    "label": "None",
    "useAnnotation": false,
    "insertHierarchy": false,
    "annotationLabel": null
]

// 获取所有 PathClass 标签
def getAllPathClasses = { imageData ->
    println "正在获取所有 PathClass 标签..."
    def defaultClasses = [
        PathClass.getInstance("Positive"),
        PathClass.getInstance("Negative"),
        PathClass.getInstance("Tumor"),
        PathClass.getInstance("Stroma"),
        PathClass.getInstance("Immune cells"),
        PathClass.getInstance("Necrosis"),
        PathClass.getInstance("Other"),
        PathClass.getInstance("Region*"),
        PathClass.getInstance("Ignore*")
    ].unique { it.toString() }

    def annotations = imageData.getHierarchy().getAnnotationObjects()
    def detections = imageData.getHierarchy().getDetectionObjects()
    def existingClasses = (annotations + detections).collect { it.getPathClass() }.findAll { it != null }.unique { it.toString() }

    def allClasses = (defaultClasses + existingClasses).unique { it.toString() }.collect { it.toString() }
    println "找到的标签：${allClasses}"
    return allClasses
}

// 关闭之前打开的相同窗口
def closeExistingDialogs = { currentStage ->
    Platform.runLater {
        def stages = Window.getWindows().findAll {
            it instanceof Stage && it.isShowing() && ((Stage) it).title == "生成ROI" && it != currentStage
        }
        stages.each { stage ->
            ((Stage) stage).close()
            println "已关闭一个现有的生成ROI窗口"
        }
    }
}

// 核心功能：生成ROI
def runFunction = { imageData, settings, statusLabel = null ->
    if (!imageData) {
        def message = "错误：图像数据未加载，无法生成 ROI！"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }
    def hierarchy = imageData.getHierarchy()
    def server = imageData.getServer()
    def plane = ImagePlane.getDefaultPlane()

    def numROIs = settings.numROIs
    def shapeType = settings.shapeType
    def width = settings.width as int // 强制转换为整数
    def height = settings.height as int // 强制转换为整数
    def label = settings.label == "None" ? null : PathClass.fromString(settings.label.toString())
    def useAnnotation = settings.useAnnotation
    def insertHierarchy = settings.insertHierarchy
    def annotationLabelStr = settings.annotationLabel?.toString()
    def annotationLabel = annotationLabelStr ? PathClass.fromString(annotationLabelStr) : null

    println "生成参数 - 数量：${numROIs}, 形状：${shapeType}, 尺寸：${width}x${height} 像素, 标签：${label}, 区域：${useAnnotation ? '指定区域' : '整个图像'}, 插入层级：${insertHierarchy}"

    def bounds
    def parentAnnotation = null
    if (useAnnotation) {
        def annotations = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == annotationLabel }
        if (annotations.isEmpty()) {
            def message = "警告：未找到标签为 ${annotationLabelStr} 的注释区域！回退到整个图像模式。"
            if (statusLabel) statusLabel.setText(message)
            println message
            bounds = new java.awt.Rectangle(0, 0, server.getWidth(), server.getHeight())
            useAnnotation = false
        } else {
            parentAnnotation = annotations[new Random().nextInt(annotations.size())]
            def annotationROI = parentAnnotation.getROI()
            bounds = new java.awt.Rectangle(
                annotationROI.getBoundsX() as int,
                annotationROI.getBoundsY() as int,
                annotationROI.getBoundsWidth() as int,
                annotationROI.getBoundsHeight() as int
            )
            println "找到注释区域，边界：${bounds}"
        }
    } else {
        bounds = new java.awt.Rectangle(0, 0, server.getWidth(), server.getHeight())
        println "使用整个图像，边界：${bounds}"
    }

    def generatedROIs = []
    generateShapes(numROIs, width, height, shapeType, bounds, plane, generatedROIs, label, parentAnnotation)

    if (generatedROIs.isEmpty()) {
        def message = "错误：未生成任何 ROI，可能是由于区域太小、尺寸过大或重叠检查失败。"
        if (statusLabel) statusLabel.setText(message)
        println message
        return false
    }

    if (insertHierarchy && useAnnotation && parentAnnotation) {
        generatedROIs.each { roi ->
            parentAnnotation.addPathObject(roi)
        }
        println "ROI 插入到标签 ${annotationLabelStr} 的注释层级中。"
    } else {
        hierarchy.addPathObjects(generatedROIs)
        println "ROI 作为独立对象添加到图像层级中。"
    }

    hierarchy.fireHierarchyChangedEvent(hierarchy)
    def message = "成功生成 ${generatedROIs.size()} 个 ROI！"
    if (statusLabel) {
        statusLabel.setText(message)
        statusLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    }
    println message
    return true
}

// 辅助函数：生成指定形状的 ROI
private def generateShapes(numShapes, width, height, shapeType, bounds, plane, generatedROIs, label, parentAnnotation) {
    def attempts = 0
    def maxAttempts = numShapes * 1000

    while (generatedROIs.size() < numShapes && attempts < maxAttempts) {
        attempts++
        // 强制x和y为整数，避免浮点数误差
        def x = (bounds.x + Math.random() * (bounds.width - width)) as int
        def y = (bounds.y + Math.random() * (bounds.height - height)) as int

        def roi = createROI(shapeType, x, y, width, height, plane)
        if (isValidROI(roi, bounds, generatedROIs, parentAnnotation)) {
            def annotation = PathObjects.createAnnotationObject(roi, label)
            generatedROIs << annotation
            println "成功生成一个 ${shapeType} ROI，位置：(${x}, ${y}), 尺寸：${width}x${height} 像素"
            // 添加调试信息，检查ROI实际尺寸
            println "ROI实际尺寸：${roi.getBoundsWidth()}x${roi.getBoundsHeight()} 像素"
        }
    }
    if (generatedROIs.size() < numShapes) {
        println "警告：仅生成 ${generatedROIs.size()} 个 ROI，部分位置可能无法满足不重叠要求。"
    }
}

// 辅助函数：创建 ROI
private def createROI(shapeType, x, y, width, height, plane) {
    // 强制width和height为整数
    def w = width as int
    def h = height as int
    println "创建ROI - 形状：${shapeType}, 位置：(${x}, ${y}), 输入尺寸：${w}x${h} 像素"

    switch (shapeType) {
        case "rectangle":
            def roi = ROIs.createRectangleROI(x, y, w, h, plane)
            println "生成的矩形ROI尺寸：${roi.getBoundsWidth()}x${roi.getBoundsHeight()} 像素"
            return roi
        case "circle":
            def diameter = w
            def roi = ROIs.createEllipseROI(x - diameter / 2, y - diameter / 2, diameter, diameter, plane)
            println "生成的圆形ROI尺寸：${roi.getBoundsWidth()}x${roi.getBoundsHeight()} 像素"
            return roi
        default:
            throw new IllegalArgumentException("不支持的形状类型：${shapeType}")
    }
}

// 辅助函数：检查 ROI 是否有效
private def isValidROI(roi, bounds, existingROIs, parentAnnotation) {
    // 检查ROI是否超出边界
    if (roi.getBoundsX() < bounds.x || roi.getBoundsY() < bounds.y ||
        roi.getBoundsX() + roi.getBoundsWidth() > bounds.x + bounds.width ||
        roi.getBoundsY() + roi.getBoundsHeight() > bounds.y + bounds.height) {
        println "ROI无效：超出边界，位置：(${roi.getBoundsX()}, ${roi.getBoundsY()}), 尺寸：${roi.getBoundsWidth()}x${roi.getBoundsHeight()}"
        return false
    }

    if (parentAnnotation) {
        def roiGeometry = roi.getGeometry()
        def annotationGeometry = parentAnnotation.getROI().getGeometry()
        if (!annotationGeometry.contains(roiGeometry)) {
            println "ROI无效：不在父注释区域内"
            return false
        }
    }

    def roiGeometry = roi.getGeometry()
    def isValid = existingROIs.every { existing ->
        def existingGeometry = existing.getROI().getGeometry()
        !roiGeometry.intersects(existingGeometry)
    }
    if (!isValid) {
        println "ROI无效：与现有ROI重叠"
    }
    return isValid
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

// 创建GUI界面
def createAndShowDialog = { paramsDir, imageData ->
    def root = new VBox(15)
    root.setPadding(new Insets(15))
    root.setStyle("-fx-background-color: #ffffff;")

    def pane = new GridPane()
    pane.setHgap(10)
    pane.setVgap(10)
    pane.setPadding(new Insets(10))
    pane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px;")

    // ROI 形状选择
    def shapeLabel = new Label("ROI 形状:")
    def shapeChoice = new ComboBox(FXCollections.observableArrayList(["Rectangle", "Circle"]))
    shapeChoice.setValue("Rectangle")
    pane.add(shapeLabel, 0, 0)
    pane.add(shapeChoice, 1, 0, 2, 1)

    // ROI 数量输入
    def numLabel = new Label("ROI 数量:")
    def roiCount = new TextField(defaultSettings.numROIs.toString())
    pane.add(numLabel, 0, 1)
    pane.add(roiCount, 1, 1, 2, 1)

    // 矩形参数
    def widthLabel = new Label("宽度（像素）：")
    def rectWidth = new TextField(defaultSettings.width.toString())
    def heightLabel = new Label("高度（像素）：")
    def rectHeight = new TextField(defaultSettings.height.toString())
    pane.add(widthLabel, 0, 2)
    pane.add(rectWidth, 1, 2)
    pane.add(heightLabel, 2, 2)
    pane.add(rectHeight, 3, 2)

    // 圆形参数
    def circleLabel = new Label("直径（像素）：")
    def circleDiameter = new TextField(defaultSettings.width.toString())
    circleDiameter.setDisable(true)
    circleDiameter.setStyle("-fx-opacity: 0.5;")
    pane.add(circleLabel, 0, 3)
    pane.add(circleDiameter, 1, 3, 2, 1)

    shapeChoice.valueProperty().addListener({ obs, oldVal, newVal ->
        if (newVal == "Rectangle") {
            rectWidth.setDisable(false)
            rectHeight.setDisable(false)
            rectWidth.setStyle("")
            rectHeight.setStyle("")
            circleDiameter.setDisable(true)
            circleDiameter.setStyle("-fx-opacity: 0.5;")
        } else if (newVal == "Circle") {
            rectWidth.setDisable(true)
            rectHeight.setDisable(true)
            rectWidth.setStyle("-fx-opacity: 0.5;")
            rectHeight.setStyle("-fx-opacity: 0.5;")
            circleDiameter.setDisable(false)
            circleDiameter.setStyle("")
        }
    })

    // 生成区域标签选择
    def allPathClasses = imageData ? getAllPathClasses(imageData) : []
    def areaLabel = new Label("生成区域标签:")
    def classChoice = new ComboBox(FXCollections.observableArrayList(["整个图像"] + allPathClasses))
    classChoice.setValue("整个图像")
    pane.add(areaLabel, 0, 4)
    pane.add(classChoice, 1, 4, 2, 1)

    // ROI 标签选择
    def roiLabel = new Label("ROI 标签:")
    def labelChoice = new ComboBox(FXCollections.observableArrayList(["None"] + allPathClasses))
    labelChoice.setValue("None")
    pane.add(roiLabel, 0, 5)
    pane.add(labelChoice, 1, 5, 2, 1)

    // 插入到区域层级选项
    def insertCheckbox = new CheckBox("插入到指定区域层级")
    insertCheckbox.setVisible(false)
    pane.add(insertCheckbox, 1, 6)

    classChoice.valueProperty().addListener({ obs, oldVal, newVal ->
        insertCheckbox.setVisible(newVal != "整个图像")
    })

    // 参数文件名和加载按钮
    def savePane = new GridPane()
    savePane.setHgap(10)
    savePane.setVgap(10)
    savePane.setPadding(new Insets(10))
    def nameLabel = new Label("参数文件名:")
    def nameField = new TextField("Roi生成参数")
    savePane.add(nameLabel, 0, 0)
    savePane.add(nameField, 1, 0)

    // 按钮区域
    def buttonBox = new HBox(20)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(10))
    buttonBox.setStyle("-fx-background-color: #ffffff;")

    def confirmButton = new Button("确认运行")
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

    def statusLabel = new Label("就绪")
    statusLabel.setStyle("-fx-text-fill: #B74747; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: #f0f0f0; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    statusLabel.setMaxWidth(Double.MAX_VALUE)
    statusLabel.setWrapText(true)
    statusLabel.setPrefHeight(100)

    confirmButton.setOnAction {
        try {
            def numROIs = roiCount.text.toInteger()
            def shapeType = shapeChoice.value == "Rectangle" ? "rectangle" : "circle"
            def width = (shapeType == "rectangle" ? rectWidth.text.toDouble() : circleDiameter.text.toDouble()) as int // 强制整数
            def height = (shapeType == "rectangle" ? rectHeight.text.toDouble() : circleDiameter.text.toDouble()) as int // 强制整数
            def label = labelChoice.value
            def useAnnotation = classChoice.value != "整个图像"
            def insertHierarchy = insertCheckbox.isSelected()
            def annotationLabel = useAnnotation ? classChoice.value : null

            if (!numROIs || numROIs < 0) throw new NumberFormatException("ROI 数量必须为正整数")
            if (!width || width <= 0) throw new NumberFormatException("宽度/直径必须为正数")
            if (shapeType == "rectangle" && (!height || height <= 0)) throw new NumberFormatException("矩形高度必须为正数")

            def settings = [
                "numROIs": numROIs,
                "shapeType": shapeType,
                "width": width,
                "height": height,
                "label": label,
                "useAnnotation": useAnnotation,
                "insertHierarchy": insertHierarchy,
                "annotationLabel": annotationLabel
            ]
            runFunction(imageData, settings, statusLabel)
        } catch (NumberFormatException e) {
            statusLabel.setText("错误：输入参数无效，请输入有效的数字！")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            println "错误：${e.message}"
        } catch (Exception e) {
            statusLabel.setText("错误：生成 ROI 失败 - ${e.message}")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            println "错误：${e.message}"
        }
    }

    saveSettingsButton.setOnAction {
        try {
            def numROIs = roiCount.text.toInteger()
            def shapeType = shapeChoice.value == "Rectangle" ? "rectangle" : "circle"
            def width = (shapeType == "rectangle" ? rectWidth.text.toDouble() : circleDiameter.text.toDouble()) as int // 强制整数
            def height = (shapeType == "rectangle" ? rectHeight.text.toDouble() : circleDiameter.text.toDouble()) as int // 强制整数
            def label = labelChoice.value
            def useAnnotation = classChoice.value != "整个图像"
            def insertHierarchy = insertCheckbox.isSelected()
            def annotationLabel = useAnnotation ? classChoice.value : null

            if (!numROIs || numROIs < 0) throw new NumberFormatException("ROI 数量必须为正整数")
            if (!width || width <= 0) throw new NumberFormatException("宽度/直径必须为正数")
            if (shapeType == "rectangle" && (!height || height <= 0)) throw new NumberFormatException("矩形高度必须为正数")

            def settings = [
                "numROIs": numROIs,
                "shapeType": shapeType,
                "width": width,
                "height": height,
                "label": label,
                "useAnnotation": useAnnotation,
                "insertHierarchy": insertHierarchy,
                "annotationLabel": annotationLabel
            ]
            def userInput = nameField.text.trim()
            if (!userInput) userInput = "default"
            def scriptName = getCurrentScriptName()
            def fileName = "${scriptName}_${userInput}"
            if (!fileName.endsWith(".json")) fileName += ".json"
            def settingsFile = new File("D:/Qupath(qupath改编)/params", fileName)
            def gson = GsonTools.getInstance()
            settingsFile.text = gson.toJson(settings)
            statusLabel.setText("参数已保存到 ${settingsFile.getAbsolutePath()}")
            statusLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            println "保存参数文件: ${settingsFile.absolutePath}"
        } catch (Exception e) {
            statusLabel.setText("错误：保存参数失败 - ${e.message}")
            statusLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            println "错误：${e.message}"
        }
    }

    closeButton.setOnAction {
        closeButton.scene.window.hide()
    }

    root.getChildren().addAll(pane, savePane, buttonBox, statusLabel)

    def stage = new Stage()
    stage.setTitle("生成ROI")
    stage.setScene(new Scene(root, 600, 500))
    stage.setResizable(true)
    stage.show()
    closeExistingDialogs(stage)
}

// 批量运行逻辑
def runBatch = { imageData, paramsFile ->
    println "批量运行开始，参数文件: ${paramsFile.absolutePath}"
    def gson = GsonTools.getInstance()
    def settings = gson.fromJson(paramsFile.text, Map)
    println "加载参数: ${settings}"
    runFunction(imageData, settings)
}

// 主逻辑：支持单独运行和批量运行
def project = qupath.getProject()
def projectDir = project.getPath().toFile().getParentFile()
def paramsDir = new File(projectDir, "params")
if (!paramsDir.exists()) paramsDir.mkdirs()

if (binding.hasVariable('paramFile') && binding.hasVariable('imageData')) {
    def paramFile = binding.getVariable('paramFile') as File
    def imageData = binding.getVariable('imageData')
    println "从管理器调用，处理图像: ${imageData.getServer().getMetadata().getName()}"
    runBatch(imageData, paramFile)
} else {
    def imageData = qupath.getViewer().getImageData() ?: null
    Platform.runLater {
        createAndShowDialog(paramsDir, imageData)
    }
}