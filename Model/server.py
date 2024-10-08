# General libraries
import os
import random
# Torch libraries
import torch 
from torch import nn 
# Flask libraries
from flask import Flask, request, jsonify
# Custom libraries
from fnn import FNN, ModelData

# General parameters
REFRESH_DATA = True

# Model data parameters
NORMALIZE_SENSOR_DATA = True
NORMALIZATION_RANGE = [-2, 2]
MAX_WEIGHT = 750
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
FNN_HIDDEN_DIM = 256
FNN_HIDDEN_LAYERS = 1
FNN_ACTIVATION = "ReLU"
FNN_DROPOUT = 0.3
FNN_OPTIMIZER = "AdamW"
FNN_LR = 0.0000001

# Model training parameters
DATA_SPLIT = [0.9, 0.075, 0.025]	# Train, validation, test
BATCH_SIZE = 16
MAX_EPOCHS = 2_000

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
	activation_fn = FNN_ACTIVATION,
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

#Route to perform predictions
@app.route('/predict', methods=['POST'])
def predict():
	# Get the data
	data = request.json
	# Data will contain a list of flattened measurements
	input_data = ModelData.format_measurements(data, MEASUREMENTS, NORMALIZE_SENSOR_DATA, NORMALIZATION_RANGE)
	input_data = torch.tensor(input_data, dtype=torch.float32).to(device)
	# Perform the prediction
	weight = model.predict(input_data)
	# Return the prediction
	return jsonify({"weight": weight})
