import json
from gps_tools import Coordinate
from gps_tools.distance import haversine_distance
import argparse

def interpolate(lat1, lon1, lat2, lon2, fraction):
    delta = haversine_distance(Coordinate(lat1, lon1), Coordinate(lat2, lon2))
    if delta == 0:
        return lat1, lon1

    lat = ((lat2 - lat1) * fraction) + lat1
    lon = ((lon2 - lon1) * fraction) + lon1

    return [lon, lat]

def resample_path_mph(points, speed_mph, interval_s=0.1):
    result, distance, time = resample_path(points, speed_mph * 0.44704, interval_s=interval_s)
    return result, distance * 0.0006213712, time

def resample_path(points, speed_mps, interval_s=0.1):
    if len(points) < 2:
        return points

    distances = [0]
    total_dist = 0
    for i in range(1, len(points)):
        d = haversine_distance(Coordinate(points[i-1][1],points[i-1][0]), Coordinate(points[i][1],points[i][0]))
        total_dist += d
        distances.append(total_dist)

    step_distance = speed_mps * interval_s
    resampled = [[points[0][0], points[0][1]]]
    traveled = 0
    i = 0

    distance_index = 1
    num_points = int(total_dist / step_distance)
    while i < num_points:
        traveled += step_distance


        while traveled > distances[distance_index]:
            distance_index +=1

        segment_length = distances[distance_index] - distances[distance_index -1]
        fraction = (traveled - distances[distance_index -1]) / segment_length
        new_point = interpolate(points[distance_index-1][1], points[distance_index-1][0],points[distance_index][1], points[distance_index][0], fraction)
        resampled.append(new_point)

        i+=1
    resampled.append([points[-1][0], points[-1][1]])

    return resampled, total_dist, (total_dist / speed_mps)


def build_path(name, speed, sample_interval, input_file):
    coordinates = get_coordinates(input_file)

    result, distance, time = resample_path_mph(coordinates, speed, interval_s=sample_interval/1000.0)
    output = {}
    output['name'] = name
    output['type'] = "Feature"
    output['properties'] = {"name": name}
    output['geometry'] = {"type": "LineString", "coordinates": result}
    output['timestamps'] = list(range(0,len(result)*sample_interval,sample_interval))

    print(f"Generated {len(result)} points for path '{name}'")
    print(f"Saving to {name}.json")
    print(f"Total distance: {distance:.2f} miles, Estimated time: {time/60:.2f} minutes at {speed} MPH")

    with open(f'{name}.json', 'w') as f:
        json.dump(output, f, indent=2)

def get_coordinates(kml):
    with open(kml, 'r') as f:
        data = f.read()
        coord_start = data.find("<coordinates>") + len("<coordinates>")
        coord_end = data.find("</coordinates>")
        coord_text = data[coord_start:coord_end].strip()
        coord_lines = coord_text.split()
        coordinates = []
        for line in coord_lines:
            lon, lat, _ = line.split(',')
            coordinates.append((float(lon), float(lat)))
        return coordinates


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Create Partner API GeoJson from KML file')
    parser.add_argument('--name', '-n', 
                        type=str, 
                        default=None,
                        help='Name for the output path (default: name of input file without extension)')
    
    parser.add_argument('--speed', '-s',
                        type=float,
                        default=50.0,
                        help='Speed in MPH for resampling (default: 50)')
    
    parser.add_argument('--interval', '-i',
                        type=int,
                        default=100,
                        help='Time interval in milliseconds between samples (default: 100)')
    
    parser.add_argument('--input', 
                        type=str,
                        required = True,
                        help='Input positions KML file')

    args = parser.parse_args()
    if args.name is None:
        args.name = args.input.split('.')[0]

    build_path(args.name, args.speed, args.interval, args.input)
