# General libraries
import os
import random
import json
# Torch libraries
import torch 
from torch import nn 
# Flask libraries
from flask import Flask, request, jsonify
# Custom libraries
from fnn import FNN, ModelData
# Email libraries
import smtplib
from email.mime.text import MIMEText

# Model data parameters
NORMALIZE_SENSOR_DATA = True
NORMALIZATION_RANGE = [-1, 1]
MAX_WEIGHT = 2000
MEASUREMENTS = [
	"accelerometer",
	# "deviceTemperature",	# Not collected, hence always equal to 0
	"gravity",
	"gyroscope",
	"linearAcceleration",
	# "orientation",		# Not collected, hence always equal to <0, 0, 0>
	# "pressure",				# Some devices may not have this sensor, may be always equal to 0
	"rotationVector",
	# "ambientTemperature"	# Some devices may not have this sensor, may be always equal to 0
]

# Model parameters
FNN_HIDDEN_DIM = 1024
FNN_HIDDEN_LAYERS = 3
FNN_ACTIVATION = "LeakyReLU"
FNN_DROPOUT = 0.5
FNN_OPTIMIZER = "AdamW"
FNN_LR = 0.0000001

# Model training parameters
DATA_SPLIT = [0.9, 0.075, 0.025]	# Train, validation, test
BATCH_SIZE = 16
MAX_EPOCHS = 750

# Flask server initializazion
app = Flask(__name__)

# Model initialization
model = None

# Model path
model_path = "./fnn.pth"

# Seed the random number generator
random_seed = 14
random.seed(random_seed)
torch.manual_seed(random_seed)

# Set the device
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Define the model
print("\nInitializing the model...")
model = FNN(
	input_dim = 1500,
	hidden_dim = FNN_HIDDEN_DIM,
	hidden_layers = FNN_HIDDEN_LAYERS,
	activation_functions = FNN_ACTIVATION,
	dropout = FNN_DROPOUT,
	optimizer = FNN_OPTIMIZER,
	output_dim = 1,
	lr = FNN_LR,
	loss_fn = nn.MSELoss(),
	device = device,
	normalization_range = NORMALIZATION_RANGE,
	max_weight=MAX_WEIGHT,
	log_on_wandb = True,
	log_on_console = False
)
print("> DONE: Model initialized successfully")

# Restore the model from the file (if it exists)
if os.path.exists(model_path):
	print("\nRestoring the model from file...")
	model.load_state_dict(torch.load(model_path))
	print("> DONE: Model restored successfully")

# Set the model to evaluation mode
model.eval()

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
	try:
		test = False
		if test:
			# Wait for some seconds then return a random weight
			import time
			time.sleep(1)
			return jsonify({"weight": 14})
		else:
			# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
			data = request.get_json()
			# Extract the measurements from the data
			measurements = data["sensor_data"]
			# print("Measurements (" + str(len(measurements)) + "):")
			# print(measurements)
			# Data will contain a list of flattened measurements
			input_data = []
			for i in range(len(measurements)):
				data_point = ModelData.format_measurements(measurements[i], MEASUREMENTS, NORMALIZE_SENSOR_DATA, NORMALIZATION_RANGE)
				input_data.extend(data_point)
			input_data = torch.tensor(input_data, dtype=torch.float64).to(device)
			# Perform the prediction
			weight,model_output = model.predict(input_data)
			# Convert the weight and model output from a tensor to a float
			weight = weight.item()
			model_output = model_output.item()
			print("Predicted weight: " + str(weight))
			print("Model output: " + str(model_output))
			# Return the prediction
			return jsonify({"weight": weight, "model_output": model_output})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"error": str(e)})

# Route to login
@app.route('/login', methods=['GET', 'POST'])
def login():
	try:
		# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
		data = request.get_json()
		# Get the "email" field (only the email field is sent to then generate a one time password sent via email to the user)
		email = data["email"]
		# Check that the email is in the correct format
		if "@" not in email or "." not in email:
			return jsonify({"success": False, "error": "Invalid email format"})
		# Generate a one time password
		otp = random.randint(100000, 999999)
		# Print the OTP
		print("OTP: " + str(otp))
		# Store the OTP in the "otp.json" file (containing a JSON objects with the emails as keys and the OTP as values), adding the new OTP to the list
		otp_file_path = "./mysite/otp.json"
		otp_file = open(otp_file_path, "r")
		otp_data = json.load(otp_file)
		otp_file.close()
		# Modify the OTP data (check if the email key is already present in the otp data, if it is, modify the OTP, if it is not, add the email and OTP)
		# otp_data.append({"email": email, "otp": str(otp)})
		otp_data[email] = str(otp)
		# Save the OTP data to the file
		otp_file = open(otp_file_path, "w")
		json.dump(otp_data, otp_file)
		otp_file.close()
		# Send the email with the OTP
		send_email(email, otp)
		# Return the response
		return jsonify({"success": True})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"success": False, "error": str(e)})
def send_email(email, otp):
	# creates SMTP session
	s = smtplib.SMTP('smtp.gmail.com', 587)
	# s = smtplib.SMTP('smtp.gmail.com', 465)
	# start TLS for security
	s.starttls()
	# My own email and password
	my_email = "foodpal.otp@gmail.com"
	my_password = "kmid sbfs thhs bjus"	# app password
	# Authentication
	s.login(my_email, my_password)
	# message to be sent
	message_subject = "FoodPal OTP Code"
	message_body = "Your one time password for FoodPal is:\n\n" + str(otp)
	message = MIMEText(message_body)
	message['Subject'] = message_subject
	message['From'] = my_email
	message['To'] = email
	# sending the mail
	s.sendmail(my_email, email, message.as_string())
	# terminating the session
	s.quit()

# Route to verify the OTP
@app.route('/verify', methods=['GET', 'POST'])
def verify():
	try:
		# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
		data = request.get_json()
		# Get the "email" and "otp" fields (both the email and the OTP are sent to then verify the OTP)
		email = data["email"]
		otp = data["otp"]
		# Verify the OTP
		otp_file_path = "./mysite/otp.json"
		users_file_path = "./mysite/users.json"
		otp_file = open(otp_file_path, "r")
		otp_data = json.load(otp_file)
		otp_file.close()
		verified = False
		user_infos = []
		followed = []
		if email in otp_data:
			if otp_data[email] == str(otp):
				verified = True
				# Remove the OTP from the file
				del otp_data[email]
				otp_file = open(otp_file_path, "w")
				json.dump(otp_data, otp_file)
				otp_file.close()
				# Add the user in the users file (if it is not already present)
				users_file = open(users_file_path, "r")
				users_data = json.load(users_file)
				users_file.close()
				# Generate a username from the email
				username = email.split("@")[0]
				if username not in users_data:
					# Check if a user with the same username but different email is already present (in this case, append a number to the username)
					# 	NOTE: first user will always be "username", second user will be "username1", third user will be "username2", etc.
					username_base = username
					username_number = 1
					while username in users_data:
						if users_data[username]["email"] == email:
							break
						username = username_base + str(username_number)
						username_number += 1
					# Add the user to the users data
					users_data[username] = {"email": email, "username": username, "infos": [], "followed": []}
					users_file = open(users_file_path, "w")
					json.dump(users_data, users_file)
					users_file.close()
				# Get the user infos
				user_infos = users_data[username]["infos"]
				followed = users_data[username]["followed"]
		# Return the response
		if not verified:
			return jsonify({"verified": False, "error": "Invalid OTP"})
		else:
			return jsonify({"verified": True, "username": username, "infos": user_infos, "followed": followed})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"verified": False, "error": str(e)})

# Route to return results about saved users in the server (as a JSONified string)
@app.route('/user_infos', methods=['GET', 'POST'])
def get_user_infos():
	try:
		# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
		data = request.get_json()
		# Get the "email" field (only the email field is sent to then generate a one time password sent via email to the user)
		username = data["username"]
		# Load the users data
		users_file_path = "./mysite/users.json"
		users_file = open(users_file_path, "r")
		users_data = json.load(users_file)
		users_file.close()
		# Check if the URL parameters contain a field "email"
		authenticated = False
		if "email" in data:
			auth_email = data["email"]
			auth_username = auth_email.split("@")[0]
			if auth_username in users_data:
				if users_data[auth_username]["email"] == auth_email:
					authenticated = True
		# Check if the username is in the users
		if username not in users_data:
			return jsonify({"found": False, "error": "Unknown username"})
		# Get the user infos
		username = users_data[username]["username"]
		user_infos = users_data[username]["infos"]
		followed = [] if not authenticated else users_data[username]["followed"]
		# Return the response
		return jsonify({"found": True, "result": {"username": username, "infos": user_infos, "followed": followed}})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"found": False, "error": str(e)})

# Route to save the user infos
@app.route('/save_user_infos', methods=['POST'])
def save_infos():
	try:
		# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
		data = request.get_json()
		# Get the "email" field (only the email field is sent to then generate a one time password sent via email to the user)
		email = data["email"]
		infos = data["infos"]
		username = email.split("@")[0]
		# Load the users data
		users_file_path = "./mysite/users.json"
		users_file = open(users_file_path, "r")
		users_data = json.load(users_file)
		users_file.close()
		# Check if the username is in the users
		if username not in users_data:
			return jsonify({"saved": False, "error": "Unknown user"})
		# Find the real username
		for user in users_data:
			if users_data[user]["email"] == email:
				username = user
				break
		# Get the current user infos (list of json objects, hence dictionaries)
		current_infos = users_data[username]["infos"]
		# Add the new infos to the list
		current_infos.append(infos)
		# Save the user infos
		users_data[username]["infos"] = current_infos
		# Save the users data
		users_file = open(users_file_path, "w")
		json.dump(users_data, users_file)
		users_file.close()
		# Return the response
		return jsonify({"saved": True, "infos": current_infos})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"saved": False, "error": str(e)})
	
# Route to update the followed users
@app.route('/update_followed', methods=['POST'])
def update_followed():
	try:
		# Get the data (data is sent through a GET request as a JSON object request with a certain JSON body, containing the sensor data)
		data = request.get_json()
		# Get the "email" field (only the email field is sent to then generate a one time password sent via email to the user)
		email = data["email"]
		followed = data["followed"]
		username = email.split("@")[0]
		# Load the users data
		users_file_path = "./mysite/users.json"
		users_file = open(users_file_path, "r")
		users_data = json.load(users_file)
		users_file.close()
		# Check if the username is in the users
		if username not in users_data:
			return jsonify({"updated": False, "error": "Unknown user"})
		# Find the real username
		for user in users_data:
			if users_data[user]["email"] == email:
				username = user
				break
		# Get the current followed users
		current_followed = users_data[username]["followed"]
		# Update the followed users
		current_followed = followed
		# Save the followed users
		users_data[username]["followed"] = current_followed
		# Save the users data
		users_file = open(users_file_path, "w")
		json.dump(users_data, users_file)
		users_file.close()
		# Return the response
		return jsonify({"updated": True})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"updated": False, "error": str(e)})
