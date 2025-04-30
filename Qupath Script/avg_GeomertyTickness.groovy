import qupath.lib.objects.PathAnnotationObject
import qupath.lib.io.GsonTools
import java.nio.file.Paths

// 配置参数
String pythonScriptPath = "F:/Qupath Script/test/calculate_thickness.py"
String geojsonPath = "F:/Qupath Script/test/output.geojson"
String outputCsvPath = "F:/Qupath Script/test/output.csv"
String targetClass = "Positive"
int numSamples = 100

// 导出GeoJSON
def annotations = getAnnotationObjects()
def gson = GsonTools.getInstance()
new File(geojsonPath).text = gson.toJson(annotations)
println "GeoJSON文件已写入：${geojsonPath}"

// 调用Python脚本
def command = ["python", pythonScriptPath, geojsonPath, outputCsvPath, targetClass, numSamples.toString()]
def process = new ProcessBuilder(command).start()
def output = process.inputStream.text
def error = process.errorStream.text
process.waitFor()
//println "Python输出: ${output}"
//if (error) println "Python错误: ${error}"

// 获取像素校准信息
def imageData = getCurrentImageData()
def server = imageData.getServer()
def pixelCalibration = server.getPixelCalibration()
double pixelWidth = pixelCalibration.getPixelWidthMicrons()  // 像素宽度（微米）
double pixelHeight = pixelCalibration.getPixelHeightMicrons() // 像素高度（微米）
String unit = pixelCalibration.getPixelWidthUnit()  // 单位（通常是"µm"）
println "像素校准: 宽度=${pixelWidth} ${unit}, 高度=${pixelHeight} ${unit}"

// 假设厚度是基于像素坐标的平均距离，取宽度和高度的平均值作为转换因子
double pixelSize = (pixelWidth + pixelHeight) / 2
println "使用平均像素大小进行转换: ${pixelSize} ${unit}/像素"

// 读取CSV并更新QuPath（带单位转换）
def csvFile = new File(outputCsvPath)
if (csvFile.exists()) {
    csvFile.eachLine { line ->
        def parts = line.split(',')
        if (parts.length == 2 && parts[0] != "ID") {  // 跳过表头
            def id = parts[0].trim()
            def thicknessPixels = parts[1].trim().toDouble()  // 原始厚度（像素）
            def thicknessPhysical = thicknessPixels * pixelSize  // 转换为物理单位（微米）

            def annotation = annotations.find { it.getID().toString() == id }
            if (annotation) {
                annotation.getMeasurementList().putMeasurement("Thickness (µm)", thicknessPhysical)
                println "ID=${id}, 像素厚度=${thicknessPixels}, 物理厚度=${thicknessPhysical} µm"
            } else {
                println "未找到匹配的注释对象：ID=${id}"
            }
        }
    }
    println "厚度已更新到QuPath（单位：${unit}）"
} else {
    println "CSV文件未找到：${outputCsvPath}"
}

// 更新层级视图
fireHierarchyUpdate()
println "厚度计算完成，结果已写入注释对象！"
