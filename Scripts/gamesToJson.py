import csv
import json

with open('games.csv') as csv_file:
    csv_reader = csv.reader(csv_file, delimiter=',')
    line_count = 0
    allData = {
        "data": []
    }
    for row in csv_reader:
        data = {}
        if line_count != 0 :
            data["category"] = row[0]
            data["description"] = row[1]
            data["difficulty"] = row[2]
            data["id"] = row[3]
            data["isDoubleItem"] = row[4] == 'TRUE'
            data["itemType"] = row[5]
            data["items"] = row[6].split(",")
            data["name"] = row[7]
            data["publishedDate"] = row[8]
            allData["data"].append(data)
        line_count += 1
    print(allData)
    
    with open('data.json', 'w') as outfile:
        json.dump(allData, outfile)
