import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.layout.HBox
import javafx.stage.DirectoryChooser
import javafx.collections.FXCollections
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.dialogs.Dialogs
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.Graphics2D
import java.awt.Color
import java.awt.BasicStroke
import qupath.lib.regions.RegionRequest
import java.awt.geom.AffineTransform
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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

// 将功能添加到 Workflow 面板
def customId = "export_annotations_tab"
Platform.runLater {
    def gui = QuPathGUI.getInstance()
    def panelTabs = gui.getAnalysisTabPane().getTabs()

    // 移除已有的同名 Tab
    panelTabs.removeIf { it.id == customId }

    // 创建新 Tab 并添加
    def pane = buildPane(annotationClasses, detectionClasses, imageNames, imageList, exportTypeOptions, defaultAnnotationClasses, defaultDetectionClasses)
    def newTab = new Tab("导出注释", pane)
    newTab.setId(customId)
    newTab.setTooltip(new Tooltip("使用自定义设置导出注释"))
    panelTabs.add(newTab)
    gui.getAnalysisTabPane().getSelectionModel().select(newTab)
}

// 构建 Workflow 面板内容
GridPane buildPane(annotationClasses, detectionClasses, imageNames, imageList, exportTypeOptions, defaultAnnotationClasses, defaultDetectionClasses) {
    def pane = new GridPane()
    pane.setPadding(new Insets(15))
    pane.setVgap(15)
    pane.setHgap(10)

    int row = 0

    // 组 1 & 2: Annotation Classes 和 Detection Classes 并排放置
    def classSection = new HBox(20)

    // 注释分类（限制显示高度并添加滚动条）
    def annotationSection = new VBox(10)
    def annotationLabel = new Label("注释分类：")
    annotationLabel.setMinWidth(150)
    def annotationScrollPane = new ScrollPane()
    def annotationVBox = new VBox(5)
    def annotationCheckBoxes = annotationClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setPrefWidth(250)
        if (className == "All") cb.setSelected(true)
        cb
    }
    annotationVBox.getChildren().addAll(annotationCheckBoxes)
    annotationScrollPane.setContent(annotationVBox)
    annotationScrollPane.setPrefHeight(150)
    annotationScrollPane.setFitToWidth(true)
    annotationSection.getChildren().addAll(annotationLabel, annotationScrollPane)

    // 检测分类（限制显示高度并添加滚动条）
    def detectionSection = new VBox(10)
    def detectionLabel = new Label("显示检测分类：")
    detectionLabel.setMinWidth(150)
    def detectionScrollPane = new ScrollPane()
    def detectionVBox = new VBox(5)
    def detectionCheckBoxes = detectionClasses.collect { className ->
        def cb = new CheckBox(className)
        cb.setPrefWidth(250)
        if (className == "All") cb.setSelected(true)
        cb
    }
    detectionVBox.getChildren().addAll(detectionCheckBoxes)
    detectionScrollPane.setContent(detectionVBox)
    detectionScrollPane.setPrefHeight(150)
    detectionScrollPane.setFitToWidth(true)
    detectionSection.getChildren().addAll(detectionLabel, detectionScrollPane)

    classSection.getChildren().addAll(annotationSection, detectionSection)
    pane.add(classSection, 0, row, 2, 1)
    row++
    pane.add(new Separator(), 0, row, 2, 1)
    row++

    // 组 3: Export Types
    def exportTypeSection = new VBox(10)
    def exportTypeLabel = new Label("导出类型：")
    exportTypeLabel.setMinWidth(150)
    def exportTypeCheckBoxes = exportTypeOptions.collect { type ->
        def cb = new CheckBox(type)
        cb.setPrefWidth(250)
        if (type == "可视化图像") cb.setSelected(true)
        cb
    }
    exportTypeSection.getChildren().addAll(exportTypeLabel, new VBox(5, *exportTypeCheckBoxes))
    pane.add(exportTypeSection, 0, row, 2, 1)
    row++
    pane.add(new Separator(), 0, row, 2, 1)
    row++

    // 组 4: Export Parameters
    def paramSection = new VBox(10)
    def paramLabel = new Label("导出参数：")
    paramLabel.setMinWidth(150)
    def paramVBox = new VBox(10)
    def labelThickness = new Label("线条粗细： ")
    def spinnerThickness = new Spinner(0.5, 5.0, 1.0, 0.5)
    labelThickness.setMinWidth(80)
    spinnerThickness.setEditable(true)
    paramVBox.getChildren().add(new HBox(10, labelThickness, spinnerThickness))
    def labelOpacity = new Label("填充透明度：")
    def spinnerOpacity = new Spinner(0.0, 1.0, 0.3, 0.1)
    labelOpacity.setMinWidth(80)
    spinnerOpacity.setEditable(true)
    paramVBox.getChildren().add(new HBox(10, labelOpacity, spinnerOpacity))
    def labelDownsample = new Label("降采样因子：")
    def spinnerDownsample = new Spinner(1.0, 10.0, 1.0, 0.5)
    labelDownsample.setMinWidth(80)
    spinnerDownsample.setEditable(true)
    paramVBox.getChildren().add(new HBox(10, labelDownsample, spinnerDownsample))
    def labelBlackBackground = new Label("背景黑色：")
    def checkBlackBackground = new CheckBox("将注释区域外变为黑色")
    labelBlackBackground.setMinWidth(80)
    checkBlackBackground.setSelected(true)
    paramVBox.getChildren().add(new HBox(10, labelBlackBackground, checkBlackBackground))
    paramSection.getChildren().addAll(paramLabel, paramVBox)
    pane.add(paramSection, 0, row, 2, 1)
    row++
    pane.add(new Separator(), 0, row, 2, 1)
    row++

    // 组 5: Select Images
    def imageSection = new VBox(10)
    def labelImages = new Label("选择图像：")
    labelImages.setMinWidth(150)
    def availableImages = new ListView(FXCollections.observableArrayList(imageNames))
    def selectedImages = new ListView(FXCollections.observableArrayList())
    availableImages.setPrefHeight(150)
    selectedImages.setPrefHeight(150)
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

    def buttonBox = new VBox(10)
    def moveRightButton = new Button(">")
    def moveLeftButton = new Button("<")
    def moveAllRightButton = new Button("R")
    def moveAllLeftButton = new Button("L")

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

    buttonBox.getChildren().addAll(moveRightButton, moveLeftButton, moveAllRightButton, moveAllLeftButton)
    def imageSelectionHBox = new HBox(10, availableImages, buttonBox, selectedImages)
    imageSection.getChildren().addAll(labelImages, imageSelectionHBox)
    pane.add(imageSection, 0, row, 2, 1)
    row++
    pane.add(new Separator(), 0, row, 2, 1)
    row++

    // 组 6: Output Settings
    def outputSection = new VBox(10)
    def outputLabel = new Label("输出设置：")
    outputLabel.setMinWidth(150)
    def outputVBox = new VBox(10)
    def labelPath = new Label("输出目录：")
    def textFieldPath = new TextField("E:\\Qupath project")
    def buttonChoosePath = new Button("浏览")
    buttonChoosePath.setOnAction {
        def chooser = new DirectoryChooser()
        chooser.setInitialDirectory(new File(textFieldPath.text).parentFile ?: new File(System.getProperty("user.home")))
        def selectedDir = chooser.showDialog(QuPathGUI.getInstance().getStage())
        if (selectedDir) textFieldPath.text = selectedDir.absolutePath
    }
    outputVBox.getChildren().add(new HBox(10, labelPath, textFieldPath, buttonChoosePath))
    def labelFormat = new Label("导出格式：")
    def comboFormat = new ComboBox(FXCollections.observableArrayList("PNG", "JPG", "TIFF"))
    comboFormat.valueProperty().set("PNG")
    outputVBox.getChildren().add(new HBox(10, labelFormat, comboFormat))
    outputSection.getChildren().addAll(outputLabel, outputVBox)
    pane.add(outputSection, 0, row, 2, 1)
    row++
    pane.add(new Separator(), 0, row, 2, 1)
    row++

    // 导出和取消按钮
    def buttonConfirm = new Button("导出")
    def buttonCancel = new Button("取消")
    def progressLabel = new Label("就绪")
    def isCancelled = new AtomicBoolean(false)

    buttonConfirm.setOnAction {
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
        println "导出格式: ${exportFormat}"

        if (selectedAnnotations.isEmpty() || selectedExportTypes.isEmpty() || selectedImageList.isEmpty()) {
            Dialogs.showErrorMessage("无效选择", "请至少选择一个注释分类、一个导出类型和一张图像。")
            return
        }

        buttonConfirm.setDisable(true)
        buttonCancel.setDisable(false)
        progressLabel.text = "正在导出..."
        isCancelled.set(false)

        Thread.start {
            try {
                exportAnnotations(selectedAnnotations, selectedDetections, selectedExportTypes, lineThickness, fillOpacity, downsample, selectedImageList, outputPath, exportFormat, imageList, progressLabel, isCancelled, blackBackground, defaultAnnotationClasses, defaultDetectionClasses)
                if (!isCancelled.get()) {
                    Platform.runLater {
                        Dialogs.showInfoNotification("导出成功", "注释已成功导出到 ${outputPath}！")
                        progressLabel.text = "导出完成！"
                    }
                } else {
                    Platform.runLater {
                        progressLabel.text = "已取消导出"
                    }
                }
            } catch (Exception e) {
                def errorMsg = "发生错误：${e.message}"
                logError(errorMsg, e, outputPath)
                Platform.runLater {
                    Dialogs.showErrorNotification("导出失败", errorMsg)
                    progressLabel.text = "导出失败！"
                }
            } finally {
                Platform.runLater {
                    buttonConfirm.setDisable(false)
                    buttonCancel.setDisable(true)
                }
            }
        }
    }

    buttonCancel.setOnAction {
        isCancelled.set(true)
        buttonCancel.setDisable(true)
        progressLabel.text = "正在取消..."
    }

    def controlButtonBox = new HBox(10, buttonConfirm, buttonCancel)
    pane.add(controlButtonBox, 1, row)
    pane.add(progressLabel, 1, row + 1)
    buttonCancel.setDisable(true)

    return pane
}

// 导出逻辑
def exportAnnotations(annotationLabels, detectionLabels, exportTypes, lineThickness, fillOpacity, downsample, selectedImages, baseOutputPath, exportFormat, imageList, progressLabel, AtomicBoolean isCancelled, boolean blackBackground, def defaultAnnotationClasses, def defaultDetectionClasses) {
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
    def futures = []

    selectedImages.each { imageName ->
        if (isCancelled.get()) return

        def future = executor.submit {
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

                        if (exportVisualized && detections) {
                            g2d.setStroke(new BasicStroke(((Double) lineThickness).floatValue()))
                            def transform = AffineTransform.getScaleInstance(1.0 / downsample, 1.0 / downsample)
                            transform.translate(-roi.getBoundsX(), -roi.getBoundsY())
                            def relevantDetections = detections.findAll { d ->
                                def pc = d.getPathClass()
                                def detectionClass = pc ? pc.toString() : "None"
                                (detectionLabels.contains("All") || detectionLabels.contains(detectionClass)) &&
                                roi.contains(d.getROI().getCentroidX(), d.getROI().getCentroidY())
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

                        def outputFile = new File(outputDir, "${name}_${idx}.${exportFormat}")
                        println "准备导出文件: ${outputFile.absolutePath}"
                        if (!isCancelled.get()) {
                            try {
                                boolean success = ImageIO.write(outputImage, exportFormat, outputFile)
                                if (success) {
                                    println "成功导出: ${outputFile.absolutePath}"
                                } else {
                                    logError("无法写入图像: ${outputFile.absolutePath} - ImageIO.write 返回 false", null, baseOutputPath)
                                }
                            } catch (IOException e) {
                                logError("写入文件失败: ${outputFile.absolutePath} - ${e.message}", e, baseOutputPath)
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
        futures << future
    }

    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.MINUTES)

    if (!isCancelled.get()) {
        def endTime = System.currentTimeMillis()
        println "为 ${selectedImages.size()} 张图像完成 ${exportTypes.size()} 种类型的导出，用时 ${(endTime - startTime) / 1000} 秒！"
    } else {
        println "导出被取消"
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
