import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.layout.HBox
import javafx.stage.Stage
import javafx.stage.Window
import javafx.collections.FXCollections
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.Graphics2D
import java.awt.Color
import qupath.lib.regions.RegionRequest
import java.awt.geom.AffineTransform
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javafx.stage.DirectoryChooser
import java.util.concurrent.atomic.AtomicBoolean
import qupath.lib.io.GsonTools

// 获取当前项目
def project = getProject()
if (!project) {
    println "请先打开一个 QuPath 项目！"
    return
}

// 获取所有切片名称
def imageList = project.getImageList()
def imageNames = imageList.collect { it.getImageName() }
println "可用图像数量: ${imageNames.size()}"

// 获取项目路径下的 classes.json 文件
def projectDir = project.getPath().getParent().toFile()
def classesFile = new File(projectDir, "classifiers/classes.json")
def annotationClasses = ["All"]
def detectionClasses = ["All"]

if (classesFile.exists()) {
    def gson = GsonTools.getInstance()
    def jsonText = classesFile.text
    def json = gson.fromJson(jsonText, Map)
    def pathClasses = json.pathClasses as List<Map>
    annotationClasses.addAll(pathClasses.collect { it.name })
    detectionClasses.addAll(pathClasses.collect { it.name })
    println "从 classes.json 加载分类: ${annotationClasses.size() - 1}"
} else {
    println "未找到 classes.json 文件，使用默认分类"
    def defaultClasses = ["Positive", "Negative", "Other", "Region*", "Tumor", "Stroma", "Immune cells", "Necrosis", "TILs", "plasma cell", "DAPI"]
    annotationClasses.addAll(defaultClasses)
    detectionClasses.addAll(defaultClasses)
}

// 定义导出类型选项
def exportTypeOptions = ["可视化图像", "原始图像"]

// 关闭之前打开的相同窗口
def closeExistingDialogs(currentStage) {
    Platform.runLater {
        def stages = Window.getWindows().findAll { 
            it instanceof Stage && it.isShowing() && ((Stage) it).title == "导出注释" && it != currentStage 
        }
        stages.each { stage ->
            ((Stage) stage).close()
            println "已关闭一个现有的导出注释窗口"
        }
    }
}

// 在 JavaFX 线程上创建并显示窗口
Platform.runLater {
    def pane = new VBox(15)
    pane.setPadding(new Insets(15))
    pane.setStyle("-fx-background-color: #ffffff;")
    pane.setPrefWidth(600)

    // 1. 注释分类和检测分类并排
    def classHBox = new HBox(20)
    classHBox.setAlignment(Pos.CENTER_LEFT)

    def annotationVBox = new VBox(5)
    def labelAnnotation = new Label("注释分类：")
    labelAnnotation.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def annotationScroll = new ScrollPane()
    def annotationCheckVBox = new VBox(5)
    def annotationCheckBoxes = annotationClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
        cb.setPrefWidth(300)
        cb.setSelected(className == "All")
        cb
    }
    annotationCheckVBox.getChildren().addAll(annotationCheckBoxes)
    annotationScroll.setContent(annotationCheckVBox)
    annotationScroll.setPrefHeight(200)
    annotationScroll.setPrefWidth(320)
    annotationScroll.setFitToWidth(true)
    annotationScroll.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px;")
    annotationVBox.getChildren().addAll(labelAnnotation, annotationScroll)

    def detectionVBox = new VBox(5)
    def labelDetection = new Label("检测分类：")
    labelDetection.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def detectionScroll = new ScrollPane()
    def detectionCheckVBox = new VBox(5)
    def detectionCheckBoxes = detectionClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
        cb.setPrefWidth(300)
        cb.setSelected(className == "All")
        cb
    }
    detectionCheckVBox.getChildren().addAll(detectionCheckBoxes)
    detectionScroll.setContent(detectionCheckVBox)
    detectionScroll.setPrefHeight(200)
    detectionScroll.setPrefWidth(320)
    detectionScroll.setFitToWidth(true)
    detectionScroll.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px;")
    detectionVBox.getChildren().addAll(labelDetection, detectionScroll)

    classHBox.getChildren().addAll(annotationVBox, detectionVBox)

    def exportParamsHBox = new HBox(20)
    exportParamsHBox.setAlignment(Pos.CENTER_LEFT)
    exportParamsHBox.setPrefWidth(600)

    def exportTypeVBox = new VBox(5)
    def labelExportType = new Label("导出类型：")
    labelExportType.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def exportTypeCheckVBox = new VBox(5)
    def exportTypeCheckBoxes = exportTypeOptions.collect { type ->
        def cb = new CheckBox(type)
        cb.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5px;")
        cb.setPrefWidth(200)
        if (type == "可视化图像") cb.setSelected(true)
        cb
    }
    exportTypeCheckVBox.getChildren().addAll(exportTypeCheckBoxes)
    exportTypeVBox.getChildren().addAll(labelExportType, exportTypeCheckVBox)

    def paramVBox = new VBox(10)
    def labelParams = new Label("导出参数：")
    labelParams.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def paramGrid = new GridPane()
    paramGrid.setHgap(10)
    paramGrid.setVgap(10)
    def labelThickness = new Label("线条粗细：")
    labelThickness.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def spinnerThickness = new Spinner(0.5, 5.0, 1.0, 0.5)
    spinnerThickness.setEditable(true)
    spinnerThickness.setPrefWidth(100)
    def labelOpacity = new Label("填充透明度：")
    labelOpacity.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def spinnerOpacity = new Spinner(0.0, 1.0, 0.3, 0.1)
    spinnerOpacity.setEditable(true)
    spinnerOpacity.setPrefWidth(100)
    def labelDownsample = new Label("降采样因子：")
    labelDownsample.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def spinnerDownsample = new Spinner(1.0, 10.0, 1.0, 0.5)
    spinnerDownsample.setEditable(true)
    spinnerDownsample.setPrefWidth(100)
    def labelBlackBackground = new Label("背景黑色：")
    labelBlackBackground.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def checkBlackBackground = new CheckBox("将注释区域外变为黑色")
    checkBlackBackground.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
//    checkBlackBackground.setSelected(true)
    paramGrid.add(labelThickness, 0, 0)
    paramGrid.add(spinnerThickness, 1, 0)
    paramGrid.add(labelOpacity, 0, 1)
    paramGrid.add(spinnerOpacity, 1, 1)
    paramGrid.add(labelDownsample, 0, 2)
    paramGrid.add(spinnerDownsample, 1, 2)
    paramGrid.add(labelBlackBackground, 0, 3)
    paramGrid.add(checkBlackBackground, 1, 3)
    paramVBox.getChildren().addAll(labelParams, paramGrid)

    exportParamsHBox.getChildren().addAll(exportTypeVBox, paramVBox)

    def imageVBox = new VBox(10)
    def labelImages = new Label("切片列表：")
    labelImages.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def imageHBox = new HBox(10)
    def availableImages = new ListView(FXCollections.observableArrayList(imageNames))
    def selectedImages = new ListView(FXCollections.observableArrayList())
    availableImages.setPrefSize(250, 150)
    selectedImages.setPrefSize(250, 150)
    availableImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)
    selectedImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)
    availableImages.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    selectedImages.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")

    availableImages.setOnMouseClicked { event ->
        if (event.clickCount == 2) {
            def selected = availableImages.getSelectionModel().getSelectedItems().toList()
            if (selected) {
                selectedImages.getItems().addAll(selected)
                availableImages.getItems().removeAll(selected)
            }
        }
    }
    selectedImages.setOnMouseClicked { event ->
        if (event.clickCount == 2) {
            def selected = selectedImages.getSelectionModel().getSelectedItems().toList()
            if (selected) {
                availableImages.getItems().addAll(selected)
                selectedImages.getItems().removeAll(selected)
            }
        }
    }

    def moveButtonVBox = new VBox(5)
    moveButtonVBox.setAlignment(Pos.CENTER)
    def moveRightButton = new Button("❯")
    def moveLeftButton = new Button("❮")
    def moveAllRightButton = new Button("❱❱")
    def moveAllLeftButton = new Button("❰❰")
    [moveRightButton, moveLeftButton, moveAllRightButton, moveAllLeftButton].each { btn ->
        btn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
        btn.setOnMouseEntered { btn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #999999; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
        btn.setOnMouseExited { btn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
    }
    moveRightButton.setOnAction {
        def selected = availableImages.getSelectionModel().getSelectedItems().toList()
        if (selected) {
            selectedImages.getItems().addAll(selected)
            availableImages.getItems().removeAll(selected)
        }
    }
    moveLeftButton.setOnAction {
        def selected = selectedImages.getSelectionModel().getSelectedItems().toList()
        if (selected) {
            availableImages.getItems().addAll(selected)
            selectedImages.getItems().removeAll(selected)
        }
    }
    moveAllRightButton.setOnAction {
        selectedImages.getItems().addAll(availableImages.getItems())
        availableImages.getItems().clear()
    }
    moveAllLeftButton.setOnAction {
        availableImages.getItems().addAll(selectedImages.getItems())
        selectedImages.getItems().clear()
    }
    moveButtonVBox.getChildren().addAll(moveRightButton, moveAllRightButton, moveLeftButton, moveAllLeftButton)
    imageHBox.getChildren().addAll(availableImages, moveButtonVBox, selectedImages)
    imageVBox.getChildren().addAll(labelImages, imageHBox)

    def outputVBox = new VBox(10)
    def labelOutput = new Label("输出设置：")
    labelOutput.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
    def outputGrid = new GridPane()
    outputGrid.setHgap(10)
    outputGrid.setVgap(10)
    def labelPath = new Label("输出目录：")
    labelPath.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def textFieldPath = new TextField("E:\\Qupath project")
    textFieldPath.setPrefWidth(300)
    textFieldPath.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    def buttonChoosePath = new Button("浏览")
    buttonChoosePath.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    buttonChoosePath.setOnMouseEntered { buttonChoosePath.setStyle("-fx-background-color: linear-gradient(to bottom, #1976D2, #2196F3); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    buttonChoosePath.setOnMouseExited { buttonChoosePath.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
    buttonChoosePath.setOnAction {
        def chooser = new DirectoryChooser()
        chooser.setInitialDirectory(new File(textFieldPath.text).parentFile ?: new File(System.getProperty("user.home")))
        def selectedDir = chooser.showDialog(null)
        if (selectedDir) textFieldPath.text = selectedDir.absolutePath
    }
    def labelFormat = new Label("导出格式：")
    labelFormat.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
    def comboFormat = new ComboBox(FXCollections.observableArrayList("PNG", "JPG", "TIFF"))
    comboFormat.valueProperty().set("PNG")
    comboFormat.setPrefWidth(100)
    comboFormat.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
    outputGrid.add(labelPath, 0, 0)
    outputGrid.add(textFieldPath, 1, 0)
    outputGrid.add(buttonChoosePath, 2, 0)
    outputGrid.add(labelFormat, 0, 1)
    outputGrid.add(comboFormat, 1, 1)
    outputVBox.getChildren().addAll(labelOutput, outputGrid)

    def buttonHBox = new HBox(20)
    buttonHBox.setAlignment(Pos.CENTER)
    buttonHBox.setPadding(new Insets(10))
    def exportButton = new Button("导出")
    exportButton.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    exportButton.setOnMouseEntered { exportButton.setStyle("-fx-background-color: linear-gradient(to bottom, #45a049, #4CAF50); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    exportButton.setOnMouseExited { exportButton.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
    def cancelButton = new Button("取消")
    cancelButton.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    cancelButton.setOnMouseEntered { cancelButton.setStyle("-fx-background-color: linear-gradient(to bottom, #d32f2f, #f44336); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
    cancelButton.setOnMouseExited { cancelButton.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
    def progressLabel = new Label("就绪")
    progressLabel.setStyle("-fx-text-fill: #B74747; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: #f0f0f0; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
    progressLabel.setMaxWidth(Double.MAX_VALUE)
    progressLabel.setWrapText(true)
    progressLabel.setPrefHeight(100)
    def isCancelled = new AtomicBoolean(false)
    buttonHBox.getChildren().addAll(exportButton, cancelButton)

    pane.getChildren().addAll(classHBox, exportParamsHBox, imageVBox, outputVBox, buttonHBox, progressLabel)

    def stage = new Stage()
    stage.setTitle("导出注释")
    stage.setScene(new Scene(pane, 600, 800))
    stage.setResizable(false)

    // 关闭之前打开的相同窗口
    closeExistingDialogs(stage)

    exportButton.setOnAction {
        def selectedAnnotations = annotationCheckBoxes.findAll { it.isSelected() }.collect { it.getText() }
        def selectedDetections = detectionCheckBoxes.findAll { it.isSelected() }.collect { it.getText() }
        def selectedExportTypes = exportTypeCheckBoxes.findAll { it.isSelected() }.collect { it.getText() }
        def lineThickness = spinnerThickness.getValue()
        def fillOpacity = spinnerOpacity.getValue()
        def downsample = spinnerDownsample.getValue()
        def selectedImageList = selectedImages.getItems()
        def outputPath = textFieldPath.text
        def exportFormat = comboFormat.getValue().toLowerCase()
        def blackBackground = checkBlackBackground.isSelected()

        println "选择的注释分类: ${selectedAnnotations}"
        println "选择的检测分类: ${selectedDetections}"
        println "选择的导出类型: ${selectedExportTypes}"
        println "选择的图像: ${selectedImageList}"
        println "输出路径: ${outputPath}"
        println "线条粗细: ${lineThickness}, 填充透明度: ${fillOpacity}"

        if (selectedAnnotations.isEmpty() || selectedExportTypes.isEmpty() || selectedImageList.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "请至少选择一个注释分类、一个导出类型和一张图像。").showAndWait()
            return
        }

        exportButton.setDisable(true)
        cancelButton.setDisable(false)
        progressLabel.setText("正在导出...")
        progressLabel.setStyle("-fx-text-fill: #B74747; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: #f0f0f0; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
        isCancelled.set(false)

        Thread.start {
            try {
                exportAnnotationsAndDetections(selectedAnnotations, selectedDetections, selectedExportTypes, lineThickness, fillOpacity, downsample, selectedImageList, outputPath, exportFormat, imageList, progressLabel, isCancelled, blackBackground)
                if (!isCancelled.get()) {
                    Platform.runLater {
                        progressLabel.setText("导出完成！")
                        progressLabel.setStyle("-fx-text-fill: green; -fx-border-color: #799B3B; -fx-background-color: #E6F4EA; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
                        exportButton.setDisable(false)
                        cancelButton.setDisable(true)
                        new Alert(Alert.AlertType.INFORMATION, "注释已成功导出到 ${outputPath}！").showAndWait()
                    }
                } else {
                    Platform.runLater {
                        progressLabel.setText("已取消导出")
                        progressLabel.setStyle("-fx-text-fill: #B74747; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: #f0f0f0; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
                        exportButton.setDisable(false)
                        cancelButton.setDisable(true)
                    }
                }
            } catch (Exception e) {
                def errorMsg = "发生错误：${e.message}"
                logError(errorMsg, e, outputPath)
                Platform.runLater {
                    progressLabel.setText("导出失败！")
                    progressLabel.setStyle("-fx-text-fill: red; -fx-border-color: #FF0000; -fx-background-color: #FFE6E6; -fx-padding: 10px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
                    exportButton.setDisable(false)
                    cancelButton.setDisable(true)
                    new Alert(Alert.AlertType.ERROR, errorMsg).showAndWait()
                }
            }
        }
    }

    cancelButton.setDisable(true)

    stage.show()
}

// 修改后的导出逻辑，支持 Geometry 和检测对象的可视化
def exportAnnotationsAndDetections(annotationLabels, detectionLabels, exportTypes, lineThickness, fillOpacity, downsample, selectedImages, baseOutputPath, exportFormat, imageList, progressLabel, AtomicBoolean isCancelled, boolean blackBackground) {
    def startTime = System.currentTimeMillis()
    def baseOutputDir = new File(baseOutputPath)
    if (!baseOutputDir.exists()) {
        println "创建输出目录: ${baseOutputPath}"
        baseOutputDir.mkdirs()
    }
    if (!baseOutputDir.canWrite()) {
        throw new IOException("输出目录不可写: ${baseOutputPath}")
    }

    def executor = Executors.newFixedThreadPool(4)
    def totalTasks = selectedImages.size() * exportTypes.size()
    def completedTasks = new java.util.concurrent.atomic.AtomicInteger(0)

    def serverCache = [:]
    selectedImages.each { imageName ->
        if (isCancelled.get()) return

        executor.submit {
            if (isCancelled.get()) return
            try {
                println "处理图像: ${imageName}"
                def entry = imageList.find { it.getImageName() == imageName }
                if (!entry) {
                    println "未找到图像条目: ${imageName}"
                    return
                }
                def imageData = entry.readImageData()
                def server = imageData.getServer()
                serverCache[imageName] = [
                    server: server,
                    annotations: imageData.getHierarchy().getAnnotationObjects(),
                    detections: imageData.getHierarchy().getDetectionObjects()
                ]

                def annotations = serverCache[imageName].annotations
                def detections = serverCache[imageName].detections
                println "图像 ${imageName} 包含 ${annotations.size()} 个注释和 ${detections.size()} 个检测"

                def filteredAnnotations = annotations.findAll { annotation ->
                    def pc = annotation.getPathClass()
                    def annotationClass = pc ? pc.toString() : "未命名"
                    def match = (annotationLabels.contains("All") || annotationLabels.contains(annotationClass))
                    if (match) println "匹配注释: ${annotationClass}"
                    match
                }
                println "图像 ${imageName} 过滤后有 ${filteredAnnotations.size()} 个注释"

                if (filteredAnnotations.isEmpty()) {
                    println "图像 ${imageName} 没有匹配的注释，跳过导出"
                    return
                }

                exportTypes.each { exportType ->
                    if (isCancelled.get()) return
                    def exportVisualized = (exportType == "可视化图像")
                    def subFolderName = exportType.replaceAll(' ', '_')
                    def outputDir = new File(baseOutputDir, "${imageName}/${subFolderName}")
                    if (!outputDir.exists()) {
                        println "创建子目录: ${outputDir.absolutePath}"
                        outputDir.mkdirs()
                    }

                    filteredAnnotations.eachWithIndex { annotation, idx ->
                        if (isCancelled.get()) return
                        def roi = annotation.getROI()
                        if (roi == null) {
                            println "跳过无效Annotation: ${annotation.getPathClass() ?: '未命名'} (ROI 为 null)"
                            return
                        }
                        def rawName = annotation.getPathClass() ? annotation.getPathClass().toString() : "Annotation_${idx}"
                        def name = rawName.replaceAll('[\\s*/\\\\:?<>|]', '_')
                        def outputFile = new File(outputDir, "${name}_${idx}.${exportFormat}")

                        def request = RegionRequest.createInstance(server.getPath(), downsample, roi)
                        BufferedImage img = server.readBufferedImage(request)

                        def width = (int)(roi.getBoundsWidth() / downsample + 0.5)
                        def height = (int)(roi.getBoundsHeight() / downsample + 0.5)
                        BufferedImage outputImage
                        if (exportFormat == "jpg") {
                            outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                        } else {
                            outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                        }
                        Graphics2D g2d = outputImage.createGraphics()

                        if (blackBackground) {
                            g2d.setColor(Color.BLACK)
                            g2d.fillRect(0, 0, width, height)
                            def transform = AffineTransform.getScaleInstance(1.0 / downsample, 1.0 / downsample)
                            transform.translate(-roi.getBoundsX(), -roi.getBoundsY())
                            def localShape = transform.createTransformedShape(roi.getShape())
                            g2d.setClip(localShape)
                        }

                        g2d.drawImage(img, 0, 0, width, height, null)

                        if (exportVisualized) {
                            def transform = AffineTransform.getScaleInstance(1.0 / downsample, 1.0 / downsample)
                            transform.translate(-roi.getBoundsX(), -roi.getBoundsY())

                            // 绘制 Geometry 注释
                            def annotationShape = transform.createTransformedShape(roi.getShape())
                            def pathClass = annotation.getPathClass()
                            def colorRGB = pathClass != null ? pathClass.getColor() : Color.RED.getRGB() // 默认红色
                            def r = (colorRGB >> 16) & 0xFF
                            def g = (colorRGB >> 8) & 0xFF
                            def b = colorRGB & 0xFF
                            g2d.setColor(new Color(r, g, b))
                            g2d.setStroke(new java.awt.BasicStroke(2.0f)) // Geometry 使用固定粗细
                            g2d.draw(annotationShape)
                            println "绘制 Geometry: ${pathClass ?: '未命名'}"

                            // 绘制检测对象
                            if (detections && !detections.isEmpty()) {
                                def relevantDetections = detections.findAll { d ->
                                    def pc = d.getPathClass()
                                    def detectionClass = pc ? pc.toString() : "None"
                                    def dRoi = d.getROI()
                                    def match = dRoi != null && (detectionLabels.contains("All") || detectionLabels.contains(detectionClass)) && roi.getGeometry().intersects(dRoi.getGeometry())
                                    if (match) println "匹配检测对象: ${detectionClass}, 位置: (${dRoi.getCentroidX()}, ${dRoi.getCentroidY()})"
                                    match
                                }
                                println "图像 ${imageName} 包含 ${relevantDetections.size()} 个相关检测对象"

                                if (relevantDetections.isEmpty()) {
                                    println "未找到符合条件的检测对象，可能原因：分类不匹配或区域不相交"
                                }

                                relevantDetections.each { detection ->
                                    if (isCancelled.get()) {
                                        g2d.dispose()
                                        return
                                    }
                                    def dRoi = detection.getROI()
                                    def detectionShape = transform.createTransformedShape(dRoi.getShape())
                                    def detPathClass = detection.getPathClass()
                                    def detColorRGB = detPathClass != null ? detPathClass.getColor() : Color.GRAY.getRGB()
                                    def dr = (detColorRGB >> 16) & 0xFF
                                    def dg = (detColorRGB >> 8) & 0xFF
                                    def db = detColorRGB & 0xFF
                                    g2d.setStroke(new java.awt.BasicStroke(((Double) lineThickness).floatValue()))
                                    g2d.setColor(new Color(dr, dg, db))
                                    g2d.draw(detectionShape)
                                    if (fillOpacity > 0) {
                                        g2d.setColor(new Color(dr, dg, db, (int)(fillOpacity * 255)))
                                        g2d.fill(detectionShape)
                                    }
                                    println "绘制检测对象: ${detPathClass ?: '未命名'}, 颜色: (${dr}, ${dg}, ${db})"
                                }
                            } else {
                                println "图像 ${imageName} 无检测对象"
                            }
                        }

                        g2d.dispose()

                        if (!isCancelled.get()) {
                            try {
                                boolean success = ImageIO.write(outputImage, exportFormat, outputFile)
                                if (success) {
                                    println "已导出: ${outputFile.getAbsolutePath()}"
                                } else {
                                    logError("无法写入图像: ${outputFile.getAbsolutePath()}", null, baseOutputPath)
                                }
                            } catch (IOException e) {
                                logError("写入文件失败: ${outputFile.getAbsolutePath()} - ${e.message}", e, baseOutputPath)
                            }
                        }
                    }

                    if (!isCancelled.get()) {
                        Platform.runLater {
                            def completed = completedTasks.addAndGet(exportTypes.size())
                            progressLabel.text = "导出中: ${completed}/${totalTasks} 任务"
                        }
                    }
                }
            } catch (Exception e) {
                logError("处理图像 ${imageName} 时出错: ${e.message}", e, baseOutputPath)
            }
        }
    }

    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.MINUTES)

    if (!isCancelled.get()) {
        def endTime = System.currentTimeMillis()
        println "为 ${selectedImages.size()} 张图像完成 ${exportTypes.size()} 种类型的导出，用时 ${(endTime - startTime) / 1000} 秒！"
    }
}

// 日志记录函数
def logError(String message, Exception e, String outputPath) {
    def logFile = Paths.get(outputPath, "export_error.log")
    def timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
    def errorDetails = "$timestamp - $message\n${e?.stackTrace?.join('\n') ?: ''}\n\n"
    Files.write(logFile, errorDetails.bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    println message
}

println "脚本初始化成功！"