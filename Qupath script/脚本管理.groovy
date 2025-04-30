import groovy.transform.Field
import qupath.lib.gui.QuPathGUI
import javafx.application.Platform
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import qupath.lib.io.GsonTools
import groovy.lang.Binding
import groovy.lang.GroovyShell
import qupath.lib.projects.Project
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.scene.Scene
import javafx.beans.value.ChangeListener

// 使用 @Field 注解声明全局变量
@Field def qupath = QuPathGUI.getInstance()
@Field def project = qupath.getProject()
@Field def logArea
@Field def scriptsDir = PathPrefs.getUserPreferences().get("scriptsDir", "D:/Qupath script/")
@Field def paramsDir = PathPrefs.getUserPreferences().get("paramsDir", "D:/Qupath script/params/")
@Field def scriptsMap = [:]  // 存储脚本名称和路径的映射
@Field ListView availableImages  // 全局引用切片列表
@Field ListView selectedImages  // 全局引用已选择切片列表
@Field def lastImageCount = 0   // 用于检测切片数量变化

// 保存路径设置和脚本列表
def savePathsAndScripts() {
    PathPrefs.getUserPreferences().put("scriptsDir", scriptsDir)
    PathPrefs.getUserPreferences().put("paramsDir", paramsDir)
    def scriptEntries = scriptsMap.collect { name, path -> "${name}=${path}" }.join(";")
    PathPrefs.getUserPreferences().put("savedScripts", scriptEntries)
}

// 加载保存的脚本列表
def loadScripts() {
    def savedScripts = PathPrefs.getUserPreferences().get("savedScripts", "")
    if (savedScripts) {
        savedScripts.split(";").each { entry ->
            def parts = entry.split("=")
            if (parts.length == 2) {
                scriptsMap[parts[0]] = parts[1]
            }
        }
    }
}

// 更新日志区域
def updateLog(executedCount, totalCount, messages) {
    Platform.runLater {
        logArea.clear()
        logArea.appendText("${executedCount}/${totalCount}张\n")
        messages.each { msg ->
            logArea.appendText("${msg}\n")
        }
    }
}

// 更新参数文件列表，去掉前缀和 `.json` 后缀显示
def updateParamChoice(scriptChoice, paramChoice, paramsDirFile) {
    paramChoice.items.clear()
    def scriptName = scriptChoice.value
    if (scriptName) {
        def paramFiles = paramsDirFile.listFiles({ f -> f.name.endsWith('.json') } as FileFilter) ?: []
        def scriptBaseName = scriptName.replace('.groovy', '') + '_'
        def filteredParams = paramFiles.findAll { it.name.startsWith(scriptBaseName) }
        def displayNames = filteredParams.collect { it.name.replace(scriptBaseName, '').replace('.json', '') }
        paramChoice.items.addAll(displayNames)
        if (!displayNames.isEmpty()) paramChoice.setValue(displayNames[0])
    }
}

// 刷新切片列表（直接加载项目中的所有图像，保留已选择的切片）
def refreshImageList() {
    Platform.runLater {
        def currentProject = qupath.getProject()
        if (currentProject) {
            def imageList = currentProject.getImageList()
            def currentCount = imageList.size()
            if (currentCount != lastImageCount) { // 仅在图像数量变化时记录新数量，但总是刷新列表
                def allImages = imageList.collect { it.getImageName() }.toList()
                def selectedItems = selectedImages.items.toList()
                
                // 保留已选择的切片，更新 availableImages
                availableImages.items.clear()
                availableImages.items.addAll(allImages - selectedItems)
                
                // 确保 selectedItems 仍在项目中，移除不存在的切片
                selectedImages.items.clear()
                selectedImages.items.addAll(selectedItems.intersect(allImages))
                
                println "已刷新切片列表，项目: ${currentProject.getPath()}, 切片数: ${currentCount}"
                updateLog(0, 0, ["已刷新切片列表，项目: ${currentProject.getPath()}"])
                lastImageCount = currentCount
            } else {
                // 即使数量未变化，仍然刷新以确保一致性，但保留用户选择
                def allImages = imageList.collect { it.getImageName() }.toList()
                def selectedItems = selectedImages.items.toList()
                
                availableImages.items.clear()
                availableImages.items.addAll(allImages - selectedItems)
                
                selectedImages.items.clear()
                selectedImages.items.addAll(selectedItems.intersect(allImages))
                
                println "已手动刷新切片列表，项目: ${currentProject.getPath()}, 切片数: ${currentCount}"
                updateLog(0, 0, ["已手动刷新切片列表，项目: ${currentProject.getPath()}"])
            }
        } else {
            availableImages.items.clear()
            availableImages.items.add("未找到项目")
            selectedImages.items.clear()
            println "未打开项目，切片列表已清空"
            updateLog(0, 0, ["未打开项目"])
            lastImageCount = 0
        }
    }
}

// 主函数：创建并显示 GUI
def runGUI() {
    Platform.runLater(new Runnable() {
        void run() {
            def scriptsDirFile = new File(scriptsDir)
            def paramsDirFile = new File(paramsDir)
            if (!scriptsDirFile.exists()) scriptsDirFile.mkdirs()
            if (!paramsDirFile.exists()) paramsDirFile.mkdirs()

            loadScripts()

            def root = new VBox(15)
            root.setPadding(new Insets(15))
            root.setStyle("-fx-background-color: #ffffff;")

            def headerBox = new HBox(10)
            headerBox.setAlignment(Pos.CENTER_LEFT)
            def titleLabel = new Label("脚本管理器")
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
            def settingsBtn = new Button("设置")
            settingsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #9E9E9E, #757575); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            settingsBtn.setOnMouseEntered { settingsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #757575, #9E9E9E); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            settingsBtn.setOnMouseExited { settingsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #9E9E9E, #757575); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
            // 创建一个占位符区域，将按钮推到右边
            def spacer = new Region()
            HBox.setHgrow(spacer, Priority.ALWAYS) // 设置占位符区域占据所有剩余空间
            
            // 将标题、占位符和按钮添加到容器中
            headerBox.getChildren().addAll(titleLabel, spacer, settingsBtn)

            def scriptPane = new GridPane()
            scriptPane.setHgap(10)
            scriptPane.setVgap(10)
            scriptPane.setPadding(new Insets(10))
            scriptPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")

            def scriptLabel = new Label("脚本功能：")
            scriptLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
            scriptPane.add(scriptLabel, 0, 0)

            def scriptChoice = new ComboBox()
            scriptChoice.items.addAll(scriptsMap.keySet())
            scriptChoice.setPrefWidth(400)
            scriptChoice.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5;")
            if (!scriptChoice.items.isEmpty()) scriptChoice.setValue(scriptChoice.items[0])
            scriptPane.add(scriptChoice, 0, 1, 2, 1)

            def scriptButtonBox = new HBox(15)
            scriptButtonBox.setAlignment(Pos.CENTER)
            scriptButtonBox.setPadding(new Insets(10))
            scriptButtonBox.setStyle("-fx-background-color: #ffffff;")

            def addBtn = new Button("添加脚本")
            addBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            addBtn.setOnMouseEntered { addBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #1976D2, #2196F3); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            addBtn.setOnMouseExited { addBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

            def runBtn = new Button("运行脚本")
            runBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            runBtn.setOnMouseEntered { runBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #F57C00, #FF9800); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            runBtn.setOnMouseExited { runBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

            def deleteBtn = new Button("删除脚本")
            deleteBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            deleteBtn.setOnMouseEntered { deleteBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #d32f2f, #f44336); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            deleteBtn.setOnMouseExited { deleteBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

            scriptButtonBox.getChildren().addAll(addBtn, runBtn, deleteBtn)

            def paramPane = new GridPane()
            paramPane.setHgap(10)
            paramPane.setVgap(10)
            paramPane.setPadding(new Insets(10))
            paramPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")

            def paramLabel = new Label("参数文件：")
            paramLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
            paramPane.add(paramLabel, 0, 0)

            def paramChoice = new ComboBox()
            paramChoice.setPrefWidth(270)
            paramChoice.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333; -fx-padding: 5;")
            updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
            paramPane.add(paramChoice, 0, 1)

            // 添加刷新参数列表按钮
            def refreshParamsBtn = new Button("🗘")
            refreshParamsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            refreshParamsBtn.setOnMouseEntered { refreshParamsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #F57C00, #FF9800); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            refreshParamsBtn.setOnMouseExited { refreshParamsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #FF9800, #F57C00); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8px 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
            refreshParamsBtn.setOnAction {
                updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
                println "已刷新参数列表"
                updateLog(0, 0, ["已刷新参数列表"])
            }
            paramPane.add(refreshParamsBtn, 1, 1)

            scriptChoice.setOnAction { 
                updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
            }

            addBtn.onAction = {
                def fileChooser = new FileChooser()
                fileChooser.title = "选择脚本文件"
                fileChooser.initialDirectory = new File("D:/Qupath script/")
                fileChooser.extensionFilters.add(new ExtensionFilter("Groovy 脚本", "*.groovy"))
                def scriptFiles = fileChooser.showOpenMultipleDialog(qupath.getStage())
                if (scriptFiles) {
                    def newScripts = []
                    scriptFiles.each { scriptFile ->
                        def scriptName = scriptFile.name
                        if (scriptsMap.containsKey(scriptName)) {
                            println "警告：脚本 ${scriptName} 已存在，将跳过"
                            updateLog(0, 0, ["警告：脚本 ${scriptName} 已存在，将跳过"])
                        } else {
                            scriptsMap[scriptName] = scriptFile.absolutePath
                            scriptChoice.items.add(scriptName)
                            newScripts << scriptName
                        }
                    }
                    if (!newScripts.isEmpty() && scriptChoice.value == null) {
                        scriptChoice.setValue(newScripts[0])
                    }
                    println "已添加脚本: ${newScripts.join(', ')}"
                    updateLog(0, 0, ["已添加脚本: ${newScripts.join(', ')}"])
                    savePathsAndScripts()
                    updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
                    refreshImageList() // 新增脚本后刷新切片列表
                }
            }

            runBtn.onAction = {
                def selectedScript = scriptChoice.value
                if (!selectedScript) {
                    println "错误：未选择任何脚本！"
                    updateLog(0, 0, ["错误：未选择任何脚本！"])
                    return
                }
                def scriptPath = scriptsMap[selectedScript]
                def scriptFile = new File(scriptPath)
                if (!scriptFile.exists()) {
                    println "错误：脚本文件未找到: ${scriptPath}"
                    updateLog(0, 0, ["错误：脚本文件未找到: ${scriptPath}"])
                    return
                }
                try {
                    qupath.runScript(scriptFile, null)
                    println "已运行脚本: ${selectedScript}"
                    updateLog(0, 0, ["已运行脚本: ${selectedScript}"])
                } catch (Exception e) {
                    println "运行脚本时出错: ${e.message}"
                    updateLog(0, 0, ["运行脚本时出错: ${e.message}"])
                }
            }

            deleteBtn.onAction = {
                def selected = scriptChoice.value
                if (selected) {
                    scriptsMap.remove(selected)
                    scriptChoice.items.remove(selected)
                    if (!scriptChoice.items.isEmpty()) scriptChoice.setValue(scriptChoice.items[0])
                    println "已删除脚本: ${selected}"
                    updateLog(0, 0, ["已删除脚本: ${selected}"])
                    savePathsAndScripts()
                    updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
                    refreshImageList() // 删除脚本后刷新切片列表
                }
            }

            def slicePane = new GridPane()
            slicePane.setHgap(10)
            slicePane.setVgap(10)
            slicePane.setPadding(new Insets(10))
            slicePane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")

            def sliceLabel = new Label("切片列表：")
            sliceLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")
            slicePane.add(sliceLabel, 0, 0, 3, 1)

            availableImages = new ListView()
            selectedImages = new ListView()
            if (project) {
                def imageList = project.getImageList()
                availableImages.items.addAll(imageList.collect { it.getImageName() })
                lastImageCount = imageList.size()
            } else {
                availableImages.items.add("未找到项目")
            }
            availableImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)
            selectedImages.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)

            availableImages.setOnMouseClicked { event ->
                if (event.getClickCount() == 2) {
                    def selectedItems = availableImages.getSelectionModel().getSelectedItems().toList()
                    if (!selectedItems.isEmpty()) {
                        availableImages.getItems().removeAll(selectedItems)
                        selectedImages.getItems().addAll(selectedItems)
                    }
                }
            }
            selectedImages.setOnMouseClicked { event ->
                if (event.getClickCount() == 2) {
                    def selectedItems = selectedImages.getSelectionModel().getSelectedItems().toList()
                    if (!selectedItems.isEmpty()) {
                        selectedImages.getItems().removeAll(selectedItems)
                        availableImages.getItems().addAll(selectedItems)
                    }
                }
            }
            availableImages.setPrefHeight(200)
            selectedImages.setPrefHeight(200)
            availableImages.setPrefWidth(140)
            selectedImages.setPrefWidth(140)
            availableImages.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")
            selectedImages.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;")

            def buttonBox = new VBox(10)
            buttonBox.setAlignment(Pos.CENTER)
            def addSelectedBtn = new Button("❯ ")
            def addAllBtn = new Button("❱❱")
            def removeSelectedBtn = new Button("❮ ")
            def removeAllBtn = new Button("❰❰")
            def refreshBtn = new Button("🗘") // 添加刷新按钮
            [addSelectedBtn, addAllBtn, removeSelectedBtn, removeAllBtn, refreshBtn].each { btn ->
                btn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
                btn.setOnMouseEntered { btn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #999999; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
                btn.setOnMouseExited { btn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #333333; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }
            }

            buttonBox.getChildren().addAll(addSelectedBtn, addAllBtn, removeSelectedBtn, removeAllBtn, refreshBtn)

            slicePane.add(availableImages, 0, 1)
            slicePane.add(buttonBox, 1, 1)
            slicePane.add(selectedImages, 2, 1)

            addSelectedBtn.onAction = { moveItems(availableImages, selectedImages, false) }
            addAllBtn.onAction = { moveItems(availableImages, selectedImages, true) }
            removeSelectedBtn.onAction = { moveItems(selectedImages, availableImages, false) }
            removeAllBtn.onAction = { moveItems(selectedImages, availableImages, true) }
            refreshBtn.onAction = { refreshImageList() } // 刷新按钮触发刷新

            def runButtonBox = new HBox(15)
            runButtonBox.setAlignment(Pos.CENTER)
            runButtonBox.setPadding(new Insets(20))
            runButtonBox.setStyle("-fx-background-color: #ffffff;")

            def batchRunBtn = new Button("批量运行")
            batchRunBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);")
            batchRunBtn.setOnMouseEntered { batchRunBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #45a049, #4CAF50); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);") }
            batchRunBtn.setOnMouseExited { batchRunBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);") }

            batchRunBtn.onAction = {
                def selectedScript = scriptChoice.value
                if (!selectedScript) {
                    println "错误：未选择任何脚本！"
                    updateLog(0, 0, ["错误：未选择任何脚本！"])
                    return
                }
                def scriptPath = scriptsMap[selectedScript]
                def scriptFile = new File(scriptPath)
                if (!scriptFile.exists()) {
                    println "错误：脚本文件未找到: ${scriptPath}"
                    updateLog(0, 0, ["错误：脚本文件未找到: ${scriptPath}"])
                    return
                }
                def selectedParamDisplay = paramChoice.value
                if (!selectedParamDisplay) {
                    println "错误：未选择参数文件！"
                    updateLog(0, 0, ["错误：未选择参数文件！"])
                    return
                }
                // 构造完整参数文件名
                def scriptBaseName = selectedScript.replace('.groovy', '') + '_'
                def selectedParam = scriptBaseName + selectedParamDisplay + '.json'
                def paramFile = new File(paramsDirFile, selectedParam)
                if (!paramFile.exists()) {
                    println "错误：参数文件未找到: ${paramFile.absolutePath}"
                    updateLog(0, 0, ["错误：参数文件未找到: ${paramFile.absolutePath}"])
                    return
                }
                println "选择的参数文件: ${paramFile.absolutePath}"
                def selectedImageNames = selectedImages.items
                if (selectedImageNames.isEmpty()) {
                    println "错误：未选择任何图像！"
                    updateLog(0, 0, ["错误：未选择任何图像！"])
                    return
                }
                def currentImageData = qupath.getViewer().getImageData()
                def currentImageName = currentImageData ? currentImageData.getServer().getMetadata().getName() : null
                def executedCount = 0
                def totalCount = selectedImageNames.size()
                def messages = []
                selectedImageNames.each { imageName ->
                    def entry = project.getImageList().find { it.getImageName() == imageName }
                    if (!entry) {
                        println "错误：未找到图像 ${imageName}"
                        messages.add("失败执行 ${imageName} 切片: 未找到图像")
                        updateLog(executedCount, totalCount, messages)
                        return
                    }
                    def imageData = entry.readImageData()
                    println "正在处理图像: ${imageName}"
                    def binding = new Binding()
                    binding.setVariable("qupath", qupath)
                    binding.setVariable("imageData", imageData)
                    binding.setVariable("paramFile", paramFile)
                    def shell = new GroovyShell(binding)
                    try {
                        shell.evaluate(scriptFile.text)
                        executedCount++
                        messages.add("成功执行 ${imageName} 切片")
                        updateLog(executedCount, totalCount, messages)
                        entry.saveImageData(imageData)
                        println "已保存图像数据: ${imageName}"
                        if (imageName == currentImageName) {
                            def refreshDialog = new Alert(Alert.AlertType.CONFIRMATION)
                            refreshDialog.title = "刷新当前图像"
                            refreshDialog.headerText = "当前图像正在处理，是否刷新？"
                            refreshDialog.contentText = "选择“是”以刷新当前图像，选择“否”跳过刷新。"
                            def refreshResult = refreshDialog.showAndWait()
                            if (refreshResult.isPresent() && refreshResult.get() == ButtonType.OK) {
                                Platform.runLater {
                                    qupath.getViewer().setImageData(imageData)
                                    qupath.getViewer().repaint()
                                    println "已刷新当前图像: ${imageName}"
                                    messages.add("已刷新当前图像: ${imageName}")
                                    updateLog(executedCount, totalCount, messages)
                                }
                            } else {
                                println "跳过刷新当前图像: ${imageName}"
                                messages.add("跳过刷新当前图像: ${imageName}")
                                updateLog(executedCount, totalCount, messages)
                            }
                        }
                    } catch (Exception e) {
                        println "运行脚本时出错: ${e.message}"
                        messages.add("失败执行 ${imageName} 切片: ${e.message}")
                        updateLog(executedCount, totalCount, messages)
                    }
                }
                // 批量运行完成后刷新参数列表
                updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
            }

            runButtonBox.getChildren().add(batchRunBtn)

            def logLabel = new Label("提示信息：")
            logLabel.setStyle("-fx-font-size: 16px; -fx-font-family: 'italic'; -fx-font-weight: bold; -fx-text-fill: #333333;")

            logArea = new TextArea()
            logArea.setEditable(false)
            logArea.setPrefHeight(200)
            logArea.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #799B3B; -fx-border-width: 2px; -fx-border-style: dashed; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #B74747;")

            settingsBtn.onAction = {
                def settingsStage = new Stage()
                settingsStage.setTitle("设置")
                def settingsRoot = new VBox(15)
                settingsRoot.setPadding(new Insets(15))
                settingsRoot.setStyle("-fx-background-color: #ffffff;")
                def scriptsDirLabel = new Label("脚本路径：")
                scriptsDirLabel.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
                def scriptsDirField = new TextField(scriptsDir)
                scriptsDirField.setPrefWidth(300)
                def scriptsDirBtn = new Button("浏览")
                scriptsDirBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px;")
                def scriptsDirBox = new HBox(10, scriptsDirField, scriptsDirBtn)
                def paramsDirLabel = new Label("参数路径：")
                paramsDirLabel.setStyle("-fx-font-size: 14px; -fx-font-family: 'italic'; -fx-text-fill: #333333;")
                def paramsDirField = new TextField(paramsDir)
                paramsDirField.setPrefWidth(300)
                def paramsDirBtn = new Button("浏览")
                paramsDirBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #2196F3, #1976D2); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 5px 10px;")
                def paramsDirBox = new HBox(10, paramsDirField, paramsDirBtn)
                def settingsButtonBox = new HBox(20)
                settingsButtonBox.setAlignment(Pos.CENTER)
                settingsButtonBox.setPadding(new Insets(10))
                def saveBtn = new Button("保存")
                saveBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #45a049); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px;")
                def cancelBtn = new Button("取消")
                cancelBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #f44336, #d32f2f); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'italic'; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px 20px;")
                settingsButtonBox.getChildren().addAll(saveBtn, cancelBtn)
                settingsRoot.getChildren().addAll(scriptsDirLabel, scriptsDirBox, paramsDirLabel, paramsDirBox, settingsButtonBox)
                settingsStage.setScene(new Scene(settingsRoot, 400, 250))
                settingsStage.show()
                
                scriptsDirBtn.onAction = {
                    def dirChooser = new DirectoryChooser()
                    dirChooser.title = "选择脚本目录"
                    dirChooser.initialDirectory = new File(scriptsDirField.text).exists() ? new File(scriptsDirField.text) : new File(".")
                    def selectedDir = dirChooser.showDialog(settingsStage)
                    if (selectedDir) scriptsDirField.text = selectedDir.absolutePath
                }
                
                paramsDirBtn.onAction = {
                    def dirChooser = new DirectoryChooser()
                    dirChooser.title = "选择参数目录"
                    dirChooser.initialDirectory = new File(paramsDirField.text).exists() ? new File(paramsDirField.text) : new File(".")
                    def selectedDir = dirChooser.showDialog(settingsStage)
                    if (selectedDir) paramsDirField.text = selectedDir.absolutePath
                }
                
                saveBtn.onAction = {
                    scriptsDir = scriptsDirField.text
                    paramsDir = paramsDirField.text
                    savePathsAndScripts()
                    println "已保存设置 - 脚本路径: ${scriptsDir}, 参数路径: ${paramsDir}"
                    updateLog(0, 0, ["已保存设置 - 脚本路径: ${scriptsDir}, 参数路径: ${paramsDir}"])
                    scriptsDirFile = new File(scriptsDir)
                    paramsDirFile = new File(paramsDir)
                    if (!scriptsDirFile.exists()) scriptsDirFile.mkdirs()
                    if (!paramsDirFile.exists()) paramsDirFile.mkdirs()
                    updateParamChoice(scriptChoice, paramChoice, paramsDirFile)
                    refreshImageList() // 设置后刷新切片列表
                    settingsStage.close()
                }
                
                cancelBtn.onAction = {
                    settingsStage.close()
                }
            }

            root.getChildren().addAll(headerBox, scriptPane, scriptButtonBox, paramPane, sliceLabel, slicePane, runButtonBox, logLabel, logArea)

            def tabPane = qupath.getAnalysisTabPane().getTabs()
            def tabId = "script_manager_tab"
            tabPane.removeIf { it.id == tabId }
            def newTab = new Tab("脚本管理器", root)
            newTab.setId(tabId)
            tabPane.add(newTab)
            qupath.getAnalysisTabPane().selectionModel.select(newTab)

            // 添加项目监听器，仅在切片数量变化时刷新
            qupath.projectProperty().addListener({ observable, oldValue, newValue ->
                if (newValue) {
                    def imageList = newValue.getImageList()
                    if (imageList.size() != lastImageCount) {
                        refreshImageList()
                    }
                }
            } as ChangeListener)
        }
    })
}

def moveItems(source, target, all) {
    def items = all ? source.items.toList() : source.selectionModel.selectedItems.toList()
    source.items.removeAll(items)
    target.items.addAll(items)
}

// 初始运行
runGUI()