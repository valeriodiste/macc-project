
# A very simple Flask Hello World app for you to get started with...

import time
from flask import Flask, request, jsonify

app = Flask(__name__)

# CORS headers
@app.after_request
def after_request(response):
	response.headers.add('Access-Control-Allow-Origin', '*')
	response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization')
	response.headers.add('Access-Control-Allow-Methods', 'GET,POST')
	return response

#Route to perform predictions
@app.route('/predict', methods=['GET', 'POST'])
def predict():
    # Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
	print("Request: ",request)
	print("isJson: ",request.is_json)
	data = request.get_json()
	print(data)
	# Wait for some seconds then return a random weight
	time.sleep(1)
	return jsonify({"weight": 14})


