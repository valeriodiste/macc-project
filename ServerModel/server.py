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

# General parameters
# REFRESH_DATA = True
# WANDB_API_KEY = "2ba6d81dbfe138d5c7fe13aeeeaac296cb88d274"

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
		otp_file = open(otp_file_path, "r")
		otp_data = json.load(otp_file)
		otp_file.close()
		verified = False
		if email in otp_data:
			if otp_data[email] == str(otp):
				verified = True
				# Remove the OTP from the file
				del otp_data[email]
				otp_file = open(otp_file_path, "w")
				json.dump(otp_data, otp_file)
				otp_file.close()
		# Return the response
		if not verified:
			return jsonify({"verified": False, "error": "Invalid OTP"})
		else:
			return jsonify({"verified": verified})
	except Exception as e:
		print("An error occurred:\n" + str(e))
		return jsonify({"verified": False, "error": str(e)})

