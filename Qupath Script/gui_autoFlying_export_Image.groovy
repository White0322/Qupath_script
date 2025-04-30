import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.layout.HBox
import javafx.stage.Stage
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

// 默认分类列表
def defaultAnnotationClasses = ["Positive", "Negative", "Other", "Region*", "Tumor", "Stroma", "Immune cells", "Necrosis", "TILs", "plasma cell", "DAPI"]
def defaultDetectionClasses = ["Positive", "Negative", "Other", "Region*", "Tumor", "Stroma", "Immune cells", "Necrosis", "TILs", "plasma cell", "DAPI"]

// 获取所有唯一的注释分类（PathClass）
def allAnnotations = imageList.collectMany { 
    def imageData = it.readImageData()
    imageData.getHierarchy().getAnnotationObjects()
}
def annotationClasses = allAnnotations.collect { 
    it.getPathClass() ? it.getPathClass().toString() : "None" 
}.unique()
annotationClasses.addAll(defaultAnnotationClasses)
annotationClasses = annotationClasses.unique()
annotationClasses.add(0, "All")
println "注释分类数量: ${annotationClasses.size()}"

// 获取所有检测分类
def allDetections = imageList.collectMany { 
    def imageData = it.readImageData()
    imageData.getHierarchy().getDetectionObjects()
}
def detectionClasses = allDetections.collect { 
    it.getPathClass() ? it.getPathClass().toString() : "None" 
}.unique()
detectionClasses.addAll(defaultDetectionClasses)
detectionClasses = detectionClasses.unique()
detectionClasses.add(0, "All")
println "检测分类数量: ${detectionClasses.size()}"

// 定义导出类型选项
def exportTypeOptions = ["可视化图像", "原始图像"]

// 调试信息
println "发现的注释总数: ${allAnnotations.size()}"
println "发现的检测总数: ${allDetections.size()}"

// 在 JavaFX 线程上创建并显示窗口
Platform.runLater {
      def pane = new VBox(15)
    pane.setPadding(new Insets(15))
    pane.setAlignment(Pos.TOP_LEFT)

    // 1. 注释分类和检测分类并排
    def classHBox = new HBox(20)
    classHBox.setAlignment(Pos.CENTER_LEFT)

    // 注释分类
    def annotationVBox = new VBox(5)
    def labelAnnotation = new Label("注释分类：")
    labelAnnotation.setMinWidth(100)
    def annotationScroll = new ScrollPane()
    def annotationCheckVBox = new VBox(5)
    def annotationCheckBoxes = annotationClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setPrefWidth(200)
        if (className == "All") cb.setSelected(true)
        cb
    }
    annotationCheckVBox.getChildren().addAll(annotationCheckBoxes)
    annotationScroll.setContent(annotationCheckVBox)
    annotationScroll.setPrefHeight(150) // 限制高度，约显示6个分类
    annotationScroll.setFitToWidth(true)
    annotationVBox.getChildren().addAll(labelAnnotation, annotationScroll)

    // 检测分类
    def detectionVBox = new VBox(5)
    def labelDetection = new Label("检测分类：")
    labelDetection.setMinWidth(100)
    def detectionScroll = new ScrollPane()
    def detectionCheckVBox = new VBox(5)
    def detectionCheckBoxes = detectionClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setPrefWidth(200)
        if (className == "All") cb.setSelected(true)
        cb
    }
    detectionCheckVBox.getChildren().addAll(detectionCheckBoxes)
    detectionScroll.setContent(detectionCheckVBox)
    detectionScroll.setPrefHeight(150) // 限制高度，约显示6个分类
    detectionScroll.setFitToWidth(true)
    detectionVBox.getChildren().addAll(labelDetection, detectionScroll)

    classHBox.getChildren().addAll(annotationVBox, detectionVBox)

    // 2. 导出类型和导出参数并排
    def exportParamsHBox = new HBox(20)
    exportParamsHBox.setAlignment(Pos.CENTER_LEFT)

    // 导出类型
    def exportTypeVBox = new VBox(5)
    def labelExportType = new Label("导出类型：")
    labelExportType.setMinWidth(100)
    def exportTypeCheckVBox = new VBox(5)
    def exportTypeCheckBoxes = exportTypeOptions.collect { type ->
        def cb = new CheckBox(type)
        cb.setPrefWidth(200)
        if (type == "可视化图像") cb.setSelected(true)
        cb
    }
    exportTypeCheckVBox.getChildren().addAll(exportTypeCheckBoxes)
    exportTypeVBox.getChildren().addAll(labelExportType, exportTypeCheckVBox)

    // 导出参数
    def paramVBox = new VBox(10)
    def labelParams = new Label("导出参数：")
    labelParams.setMinWidth(100)
    def paramGrid = new GridPane()
    paramGrid.setHgap(10)
    paramGrid.setVgap(10)
    def labelThickness = new Label("线条粗细：")
    def spinnerThickness = new Spinner(0.5, 5.0, 1.0, 0.5)
    spinnerThickness.setEditable(true)
    spinnerThickness.setPrefWidth(100)
    def labelOpacity = new Label("填充透明度：")
    def spinnerOpacity = new Spinner(0.0, 1.0, 0.3, 0.1)
    spinnerOpacity.setEditable(true)
    spinnerOpacity.setPrefWidth(100)
    def labelDownsample = new Label("降采样因子：")
    def spinnerDownsample = new Spinner(1.0, 10.0, 1.0, 0.5)
    spinnerDownsample.setEditable(true)
    spinnerDownsample.setPrefWidth(100)
    def labelBlackBackground = new Label("背景黑色：")
    def checkBlackBackground = new CheckBox("将注释区域外变为黑色")
    checkBlackBackground.setSelected(true)
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

    // 3. 选择导出的切片
    def imageVBox = new VBox(10)
    def labelImages = new Label("选择图像：")
    labelImages.setMinWidth(100)
    def imageHBox = new HBox(10)
    def availableImages = new ListView(FXCollections.observableArrayList(imageNames))
    def selectedImages = new ListView(FXCollections.observableArrayList())
    availableImages.setPrefSize(200, 150)
    selectedImages.setPrefSize(200, 150)
    availableImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)
    selectedImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)

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
    def moveRightButton = new Button(">")
    def moveLeftButton = new Button("<")
    def moveAllRightButton = new Button(">>")
    def moveAllLeftButton = new Button("<<")
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
    moveButtonVBox.getChildren().addAll(moveRightButton, moveLeftButton, moveAllRightButton, moveAllLeftButton)
    imageHBox.getChildren().addAll(availableImages, moveButtonVBox, selectedImages)
    imageVBox.getChildren().addAll(labelImages, imageHBox)

    // 4. 输出设置
    def outputVBox = new VBox(10)
    def labelOutput = new Label("输出设置：")
    labelOutput.setMinWidth(100)
    def outputGrid = new GridPane()
    outputGrid.setHgap(10)
    outputGrid.setVgap(10)
    def labelPath = new Label("输出目录：")
    def textFieldPath = new TextField("E:\\Qupath project")
    textFieldPath.setPrefWidth(300)
    def buttonChoosePath = new Button("浏览")
    buttonChoosePath.setOnAction {
        def chooser = new DirectoryChooser()
        chooser.setInitialDirectory(new File(textFieldPath.text).parentFile ?: new File(System.getProperty("user.home")))
        def selectedDir = chooser.showDialog(null)
        if (selectedDir) textFieldPath.text = selectedDir.absolutePath
    }
    def labelFormat = new Label("导出格式：")
    def comboFormat = new ComboBox(FXCollections.observableArrayList("PNG", "JPG", "TIFF"))
    comboFormat.valueProperty().set("PNG")
    comboFormat.setPrefWidth(100)
    outputGrid.add(labelPath, 0, 0)
    outputGrid.add(textFieldPath, 1, 0)
    outputGrid.add(buttonChoosePath, 2, 0)
    outputGrid.add(labelFormat, 0, 1)
    outputGrid.add(comboFormat, 1, 1)
    outputVBox.getChildren().addAll(labelOutput, outputGrid)

    // 5. 按钮和进度
    def buttonHBox = new HBox(20)
    buttonHBox.setAlignment(Pos.CENTER)
    def exportButton = new Button("导出")
    def cancelButton = new Button("取消")
    def progressLabel = new Label("就绪")
    def isCancelled = new AtomicBoolean(false)
    buttonHBox.getChildren().addAll(exportButton, cancelButton)

    pane.getChildren().addAll(classHBox, exportParamsHBox, imageVBox, outputVBox, buttonHBox, progressLabel)

    def stage = new Stage()
    stage.setTitle("导出注释")
    stage.setScene(new Scene(pane, 800, 700))
    stage.setResizable(false)

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

        if (selectedAnnotations.isEmpty() || selectedExportTypes.isEmpty() || selectedImageList.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "请至少选择一个注释分类、一个导出类型和一张图像。").showAndWait()
            return
        }

        exportButton.setDisable(true)
        cancelButton.setDisable(false)
        progressLabel.text = "正在导出..."
        isCancelled.set(false)

        Thread.start {
            try {
                exportAnnotationsAndDetections(selectedAnnotations, selectedDetections, selectedExportTypes, lineThickness, fillOpacity, downsample, selectedImageList, outputPath, exportFormat, imageList, progressLabel, isCancelled, blackBackground, defaultAnnotationClasses, defaultDetectionClasses)
                if (!isCancelled.get()) {
                    Platform.runLater {
                        progressLabel.text = "导出完成！"
                        exportButton.setDisable(false)
                        cancelButton.setDisable(true)
                        new Alert(Alert.AlertType.INFORMATION, "注释已成功导出到 ${outputPath}！").showAndWait()
                    }
                } else {
                    Platform.runLater {
                        progressLabel.text = "已取消导出"
                        exportButton.setDisable(false)
                        cancelButton.setDisable(true)
                    }
                }
            } catch (Exception e) {
                def errorMsg = "发生错误：${e.message}"
                logError(errorMsg, e, outputPath)
                Platform.runLater {
                    progressLabel.text = "导出失败！"
                    exportButton.setDisable(false)
                    cancelButton.setDisable(true)
                    new Alert(Alert.AlertType.ERROR, errorMsg).showAndWait()
                }
            }
        }
    }

    cancelButton.setOnAction {
        isCancelled.set(true)
        cancelButton.setDisable(true)
        progressLabel.text = "正在取消..."
    }
    cancelButton.setDisable(true)

    stage.show()
}

// 导出逻辑
def exportAnnotationsAndDetections(annotationLabels, detectionLabels, exportTypes, lineThickness, fillOpacity, downsample, selectedImages, baseOutputPath, exportFormat, imageList, progressLabel, AtomicBoolean isCancelled, boolean blackBackground, def defaultAnnotationClasses, def defaultDetectionClasses) {
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
                    def match = (annotationLabels.contains("所有注释") || annotationLabels.contains(annotationClass))
                    if (match) println "匹配注释: ${annotationClass}"
                    match
                }
                println "图像 ${imageName} 过滤后有 ${filteredAnnotations.size()} 个注释"

                // 检查是否存在默认分类并提示缺失
                def missingAnnotations = defaultAnnotationClasses.findAll { !annotations.any { a -> a.getPathClass()?.toString() == it } }
                def missingDetections = defaultDetectionClasses.findAll { !detections.any { d -> d.getPathClass()?.toString() == it } }
                if (missingAnnotations) {
                    println "图像 ${imageName} 中缺少以下注释分类：${missingAnnotations.join(', ')}"
                }
                if (missingDetections) {
                    println "图像 ${imageName} 中缺少以下检测分类：${missingDetections.join(', ')}"
                }

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
                        def rawName = annotation.getPathClass() ? annotation.getPathClass().toString() : "Annotation_${idx}"
                        def name = rawName.replaceAll('[\\s*/\\\\:?<>|]', '_')
                        def outputFile = new File(outputDir, "${name}_${idx}.${exportFormat}")

                        // 创建区域请求
                        def request = RegionRequest.createInstance(server.getPath(), downsample, roi)
                        BufferedImage img = server.readBufferedImage(request)

                        // 创建输出图像，尺寸基于读取的图像
                        BufferedImage outputImage
                        if (exportFormat == "jpg") {
                            outputImage = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
                        } else {
                            outputImage = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
                        }
                        Graphics2D g2d = outputImage.createGraphics()

                        // 如果需要黑色背景，先填充整个区域
                        if (blackBackground) {
                            g2d.setColor(Color.BLACK)
                            g2d.fillRect(0, 0, img.width, img.height)
                            def transform = AffineTransform.getScaleInstance(1.0 / downsample, 1.0 / downsample)
                            transform.translate(-roi.getBoundsX(), -roi.getBoundsY())
                            def localShape = transform.createTransformedShape(roi.getShape())
                            g2d.setClip(localShape)
                        }

                        // 绘制图像
                        g2d.drawImage(img, 0, 0, img.width, img.height, null)

                        // 绘制检测对象（如果需要）
                        if (exportVisualized && detections) {
                            g2d.setStroke(new java.awt.BasicStroke(((Double) lineThickness).floatValue()))
                            def transform = AffineTransform.getScaleInstance(1.0 / downsample, 1.0 / downsample)
                            transform.translate(-roi.getBoundsX(), -roi.getBoundsY())
                            def relevantDetections = detections.findAll { d ->
                                def centroidX = d.getROI().getCentroidX()
                                def centroidY = d.getROI().getCentroidY()
                                roi.contains(centroidX, centroidY)
                            }.findAll { detection ->
                                def pc = detection.getPathClass()
                                def detectionClass = pc ? pc.toString() : "None"
                                def match = (detectionLabels.contains("All") || detectionLabels.contains(detectionClass))
                                if (match) println "匹配检测: ${detectionClass}"
                                match
                            }
                            println "图像 ${imageName} 包含 ${relevantDetections.size()} 个相关检测"

                            relevantDetections.each { detection ->
                                if (isCancelled.get()) {
                                    g2d.dispose()
                                    return
                                }
                                def detectionShape = transform.createTransformedShape(detection.getROI().getShape())
                                def pathClass = detection.getPathClass()
                                def colorRGB = pathClass != null ? pathClass.getColor() : Color.GRAY.getRGB()
                                def r = (colorRGB >> 16) & 0xFF
                                def g = (colorRGB >> 8) & 0xFF
                                def b = colorRGB & 0xFF

                                g2d.setColor(new Color(r, g, b))
                                g2d.draw(detectionShape)
                                if (fillOpacity > 0) {
                                    g2d.setColor(new Color(r, g, b, (int)(fillOpacity * 255)))
                                    g2d.fill(detectionShape)
                                }
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
                }

                if (!isCancelled.get()) {
                    Platform.runLater {
                        def completed = completedTasks.addAndGet(exportTypes.size())
                        progressLabel.text = "导出中: ${completed}/${totalTasks} 任务"
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
