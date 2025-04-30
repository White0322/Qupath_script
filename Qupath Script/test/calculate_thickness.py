import json
import numpy as np
import csv
from shapely.geometry import Polygon, LineString

def calculate_thickness_for_feature(feature, num_samples=100):
    """计算单个GeoJSON要素的环形多边形平均厚度"""
    geometry = feature.get('geometry', {})
    feature_id = feature.get('id', '未知')

    # 检查几何类型
    if geometry.get('type') != 'Polygon':
        print(f"特征 {feature_id}: 跳过非多边形几何")
        return None

    # 获取坐标
    coordinates = geometry.get('coordinates', [])
    print(f"特征 {feature_id}: 坐标环数: {len(coordinates)}")

    # 检查是否有内外环
    if len(coordinates) < 2:
        print(f"特征 {feature_id}: 环数不足（需要内外环）")
        return None

    # 提取外环和内环
    exterior = coordinates[0]
    interior = coordinates[1]
    print(f"特征 {feature_id}: 外环点数: {len(exterior)}, 内环点数: {len(interior)}")

    # 创建Shapely几何对象
    try:
        ext_line = LineString(exterior)
        int_line = LineString(interior)
    except Exception as e:
        print(f"特征 {feature_id}: 几何数据无效: {e}")
        return None

    # 检查长度
    ext_length = ext_line.length
    int_length = int_line.length
    print(f"特征 {feature_id}: 外环长度: {ext_length}, 内环长度: {int_length}")

    if ext_length == 0 or int_length == 0:
        print(f"特征 {feature_id}: 长度为0")
        return None

    # 生成采样点
    ext_points = [ext_line.interpolate(d) for d in 
                  np.linspace(0, ext_length, num_samples, endpoint=False)]
    int_points = [int_line.interpolate(d) for d in 
                  np.linspace(0, int_length, num_samples, endpoint=False)]

    # 计算平均距离
    total = 0.0
    for p in ext_points:
        total += p.distance(int_line)
    for p in int_points:
        total += p.distance(ext_line)

    thickness = total / (len(ext_points) + len(int_points))
    print(f"特征 {feature_id}: 计算厚度: {thickness}")
    return thickness

def process_geojson(geojson_path, output_csv_path, target_class="Positive", num_samples=100):
    """处理GeoJSON文件，计算每个注释的平均厚度并写入CSV文件"""
    print(f"正在处理GeoJSON文件: {geojson_path}")

    # 读取GeoJSON
    with open(geojson_path) as f:
        data = json.load(f)

    # 兼容列表或FeatureCollection格式
    features = data if isinstance(data, list) else data.get('features', [])
    print(f"找到 {len(features)} 个特征")

    # 写入CSV
    with open(output_csv_path, mode='w', newline='') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["ID", "Thickness"])  # 写入表头

        for feature in features:
            properties = feature.get('properties', {})
            classification = properties.get('classification', {})
            feature_id = feature.get('id', '未知')

            # 筛选目标分类
            if classification.get('name') != target_class:
                print(f"特征 {feature_id}: 分类不匹配（需要 {target_class}）")
                continue

            # 计算厚度
            thickness = calculate_thickness_for_feature(feature, num_samples)
            if thickness is not None:
                writer.writerow([feature_id, thickness])
                print(f"特征 {feature_id}: 已写入CSV")

    print(f"处理完成，结果已保存至: {output_csv_path}")

if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("使用方法：python script.py <输入文件.geojson> <输出CSV文件路径> [分类名称=Positive] [采样点数=100]")
        sys.exit(1)

    geojson_path = sys.argv[1]
    output_csv_path = sys.argv[2]
    target_class = sys.argv[3] if len(sys.argv) > 3 else "Positive"
    num_samples = int(sys.argv[4]) if len(sys.argv) > 4 else 100

    print(f"参数: GeoJSON={geojson_path}, CSV={output_csv_path}, 分类={target_class}, 采样点数={num_samples}")
    process_geojson(geojson_path, output_csv_path, target_class, num_samples)
