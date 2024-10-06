# General libraries
import os
import random
import json
import math
import tqdm
# Logger libraries
import wandb
from wandb.sdk import wandb_run
import logging
from pytorch_lightning.loggers import WandbLogger
# Torch libraries
import torch 
from torch import nn 
import pytorch_lightning as pl 
import torch.nn.functional as F 
# from torchvision import datasets, transforms 
from torch.utils.data import DataLoader 

# For more info: https://www.geeksforgeeks.org/training-neural-networks-using-pytorch-lightning/

# Define the model (PyTorch Lightning module, a simple feedforward neural network)
# NOTE: model takes as input the sensor data and outputs the weight of the measured object (in grams): don't use CrossEntropyLoss, use MSELoss instead
class FNN(pl.LightningModule): 

	def __init__(
			self,
			# Required arguments
			input_dim: int = 28*28,
			hidden_dim: int = 128,
			hidden_layers: int = 2,
			output_dim: int = 10,
			lr: float = 0.01,
			loss_fn = nn.MSELoss(),
			device = torch.device("cuda" if torch.cuda.is_available() else "cpu"),
			# Optional arguments
			**kwargs
		): 

		super(FNN, self).__init__() 

		# Define the hyperparameters
		self.input_dim = input_dim
		self.hidden_dim = hidden_dim
		self.hidden_layers = hidden_layers
		self.output_dim = output_dim
		self.lr = lr
		self.loss = loss_fn
		self.kwargs = kwargs
		# self.device = device
		# self.save_hyperparameters()

		# Print the final hyperparameters
		print("\nFinal hyperparameters:")
		print("> Input dimension: ", self.input_dim)
		print("> Hidden dimension: ", self.hidden_dim)
		print("> Hidden layers: ", self.hidden_layers)
		print("> Output dimension: ", self.output_dim)
		print("> Learning rate: ", self.lr)
		print("> Loss function: ", self.loss)

		# Define the model architecture
		self.layers = []
		self.layers.append(nn.Linear(self.input_dim, self.hidden_dim, dtype=torch.float64))
		self.layers.append(nn.ReLU())
		for _ in range(self.hidden_layers):
			self.layers.append(nn.Linear(self.hidden_dim, self.hidden_dim, dtype=torch.float64))
			self.layers.append(nn.ReLU())
		self.layers.append(nn.Linear(self.hidden_dim, self.output_dim, dtype=torch.float64))
		self.model = nn.Sequential(*self.layers)

		# store variables and lists of losses and accuracies for logging
		self.train_losses = []
		self.val_losses = []
		self.train_accuracies = []
		self.val_accuracies = []
		self.test_losses = []
		self.test_accuracies = []

	# def forward(self, x): 
	# 	return self.model(x)

	def configure_optimizers(self): 
		optimizer = torch.optim.Adam(self.model.parameters(), lr=self.lr)
		return optimizer
	
	def _step(self, batch, batch_idx):
		# Get the data and the target
		data, target = batch
		# Forward pass
		output = self.model(data)
		# Calculate the loss
		loss = self.loss(output, target)
		# Calculate the accuracy
		accuracy = 1.0 - torch.mean(torch.abs(target - output))
		# Return the loss and accuracy
		return loss, accuracy
	
	def training_step(self, batch, batch_idx):
		loss, accuracy = self._step(batch, batch_idx)
		self.train_losses.append(loss)
		self.train_accuracies.append(accuracy)
		return loss
	
	def validation_step(self, batch, batch_idx):
		loss, accuracy = self._step(batch, batch_idx)
		self.val_losses.append(loss)
		self.val_accuracies.append(accuracy)
		return loss
	
	def test_step(self, batch, batch_idx):
		loss, accuracy = self._step(batch, batch_idx)
		self.test_losses.append(loss)
		self.test_accuracies.append(accuracy)
		return loss
	
	def print_epoch_end(self):
		log_every_n_epochs = 1
		epoch = self.current_epoch
		if epoch % log_every_n_epochs == 0:
			if len(self.train_losses)>0 or len(self.train_accuracies)>0 or len(self.val_losses)>0 or len(self.val_accuracies)>0 or len(self.test_losses)>0 or len(self.test_accuracies)>0:
				print("\nEpoch: ", epoch)
			if len(self.train_losses)>0:
				train_loss = torch.stack(self.train_losses).mean()
				train_accuracy = torch.stack(self.train_accuracies).mean()
				self.log("train_loss", train_loss)
				self.log("train_accuracy", train_accuracy)
				print("Train loss: ", round(train_loss.item(), 4), " | Train accuracy: ", round(train_accuracy.item(), 4))
			if len(self.val_losses)>0:
				val_loss = torch.stack(self.val_losses).mean()
				val_accuracy = torch.stack(self.val_accuracies).mean()
				self.log("val_loss", val_loss)
				self.log("val_accuracy", val_accuracy)
				print("Validation loss: ", round(val_loss.item(), 4), " | Validation accuracy: ", round(val_accuracy.item(), 4))
			if len(self.test_losses)>0:
				test_loss = torch.stack(self.test_losses).mean()
				test_accuracy = torch.stack(self.test_accuracies).mean()
				self.log("test_loss", test_loss)
				self.log("test_accuracy", test_accuracy)
				print("Test loss: ", round(test_loss.item(), 4), " | Test accuracy: ", round(test_accuracy.item(), 4))
	
	# def on_train_epoch_start(self):
	# 	self.train_losses = []
	# 	self.train_accuracies = []

	# def on_train_epoch_end(self):
	# 	epoch = self.current_epoch
	# 	if epoch % 10 == 0:
	# 		loss = torch.stack(self.train_losses).mean()
	# 		accuracy = torch.stack(self.train_accuracies).mean()
	# 		self.log("train_loss", loss)
	# 		self.log("train_accuracy", accuracy)
	# 		print("\nTrain loss: ", round(loss.item(), 4), " | Train accuracy: ", round(accuracy.item(), 4))

	# def on_validation_epoch_start(self):
	# 	self.val_losses = []
	# 	self.val_accuracies = []

	# def on_validation_epoch_end(self):
	# 	epoch = self.current_epoch
	# 	if epoch % 10 == 0:
	# 		loss = torch.stack(self.val_losses).mean()
	# 		accuracy = torch.stack(self.val_accuracies).mean()
	# 		self.log("val_loss", loss)
	# 		self.log("val_accuracy", accuracy)
	# 		print("\nValidation loss: ", round(loss.item(), 4), " | Validation accuracy: ", round(accuracy.item(), 4))

	# def on_test_epoch_start(self):
	# 	self.test_losses = []
	# 	self.test_accuracies = []

	# def on_test_epoch_end(self):
	# 	epoch = self.current_epoch
	# 	if epoch % 10 == 0:
	# 		loss = torch.stack(self.test_losses).mean()
	# 		accuracy = torch.stack(self.test_accuracies).mean()
	# 		self.log("test_loss", loss)
	# 		self.log("test_accuracy", accuracy)
	# 		print("\nTest loss: ", round(loss.item(), 4), " | Test accuracy: ", round(accuracy.item(), 4))

	def on_train_epoch_start(self):
		self.train_losses = []
		self.train_accuracies = []

	def on_validation_epoch_start(self):
		self.val_losses = []
		self.val_accuracies = []

	def on_test_epoch_start(self):
		self.test_losses = []
		self.test_accuracies = []

	def on_validation_epoch_end(self):
		self.print_epoch_end()

	def on_test_epoch_end(self):
		self.print_epoch_end()

# Define the data module (PyTorch Lightning DataModule)
class ModelData(pl.LightningDataModule): 

	# Initialize the data module
	def __init__(
			self,
			# Required arguments
			measurements_data,
			calibration_data,
			measurement_types,
			normalize_sensor_data,
			normalization_range,
			# Hyperparameters
			data_split,	# Train, validation, test
			batch_size,
			device,
			# Optional arguments
			**kwargs
		): 
		super(ModelData, self).__init__() 
		# Define the hyperparameters
		self.measurements_data = measurements_data
		self.calibration_data = calibration_data
		self.measurement_types = measurement_types
		self.normalize_sensor_data = normalize_sensor_data
		self.normalization_range = normalization_range
		self.max_weight = 1000
		self.data_split = data_split
		self.batch_size = batch_size
		self.device = device
		self.kwargs = kwargs
		# Build the data (train, validation, test)
		self.train_data, self.val_data, self.test_data = self.get_dataset(self.measurements_data, self.calibration_data, self.measurement_types, self.data_split, self.normalize_sensor_data, self.normalization_range)
		self.input_dim = len(self.train_data[0][0])

	# Build the dataset as <sensor_data, weight> pairs where sensor_data contains the calibration data and the measurement data, and weight is the weight of the measured object
	def get_dataset(self, measurements_data, calibration_data, measurement_types, data_split, normalize_sensor_data, normalization_range):
		# Merge the measurements and calibration data into array data
		data = []
		for measurement in measurements_data:
			# Get the calibration index, sensor data, and weight
			calibration_index = measurement["calibration_index"]
			measurement_sensors_data = measurement["sensor_datas"]
			calibration_sensor_data = calibration_data[calibration_index]["sensor_datas"]
			weight = measurement["weight"]
			# Build the data object (normalize all data in the given range if needed)
			data_object = []
			for i in range(len(measurement_sensors_data)):
				single_measurement_sensors_data = measurement_sensors_data[i]
				single_calibration_sensor_data = calibration_sensor_data[i]
				for measurement_type in measurement_types:
					if single_measurement_sensors_data[measurement_type] is not None and single_calibration_sensor_data[measurement_type] is not None:
						# Get the calibration and measurement data
						calibration = single_calibration_sensor_data[measurement_type]
						measurement = single_measurement_sensors_data[measurement_type]
						# Normalize and convert data to tensor (if needed)
						if normalize_sensor_data:
							if measurement_type == "accelerometer":
								# Accelerometer is a <x, y, z> tuple with coordinates in m/s^2 (safely assume data is in ranges [-10, 10], normalize to [-1, 1])
								calibration = torch.tensor([val/10.0 for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
							elif measurement_type == "deviceTemperature":
								# Device temperature is a float value in Celsius (safely assume data is in ranges [0, 50], normalize to [-1, 1])
								calibration = torch.tensor([(calibration+50)/100.0], dtype=torch.float64)
								measurement = torch.tensor([(measurement+50)/100.0], dtype=torch.float64)
							elif measurement_type == "gravity":
								# Gravity is a <x, y, z> tuple with coordinates in m/s^2 (safely assume data is in ranges [-10, 10], normalize to [-1, 1], since gravity on Earth is 9.8 m/s^2 on average, never varying by more than 0.2, and can be negative or positive depending on the orientation)
								calibration = torch.tensor([val/10.0 for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
							elif measurement_type == "gyroscope":
								# Gyroscope is a <x, y, z> tuple with coordinates in rad/s (we can assume data to be 360 degrees per second at most, in both directions, normalize to [-1, 1])
								calibration = torch.tensor([val/(2*math.pi) for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val/(2*math.pi) for val in measurement], dtype=torch.float64)
							elif measurement_type == "linearAcceleration":
								# Linear acceleration is a <x, y, z> tuple with coordinates in m/s^2 (during measurements, acceleration should be small, assume data already in range [-10, 10], normalize to [-1, 1])
								calibration = torch.tensor([val/10.0 for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
							elif measurement_type == "orientation":
								# Orientation is a <x, y, z> tuple with coordinates in degrees (safely assume data is in ranges [-180, 180], normalize to [-1, 1])
								calibration = torch.tensor([val/180.0 for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val/180.0 for val in measurement], dtype=torch.float64)
							elif measurement_type == "pressure":
								# Pressure is a float value in hPa (safely assume data is in ranges [900, 1100], normalize to [-1, 1])
								calibration = torch.tensor([(calibration-900)/200.0], dtype=torch.float64)
								measurement = torch.tensor([(measurement-900)/200.0], dtype=torch.float64)
							elif measurement_type == "rotationVector":
								# Rotation vector is a <x, y, z> tuple with coordinates in unitless values (safely assume data is in ranges [-1, 1], no need to normalize to [-1, 1])
								calibration = torch.tensor([val for val in calibration], dtype=torch.float64)
								measurement = torch.tensor([val for val in measurement], dtype=torch.float64)
							elif measurement_type == "ambientTemperature":
								# Ambient temperature is a float value in Celsius (safely assume data is in ranges [0, 50], normalize to [-1, 1])
								calibration = torch.tensor([(calibration+50)/100.0], dtype=torch.float64)
								measurement = torch.tensor([(measurement+50)/100.0], dtype=torch.float64)
							else:
								# Throw an error if the measurement type is not recognized
								raise ValueError("Unknown measurement type: " + measurement_type)
							# Take all data normalized in range [-1,1] and map it to a new range
							calibration = (calibration - (-1)) * (normalization_range[1] - normalization_range[0]) / (1 - (-1)) + normalization_range[0]
							measurement = (measurement - (-1)) * (normalization_range[1] - normalization_range[0]) / (1 - (-1)) + normalization_range[0]
						else:
							# Convert data to tensor (without normalization)
							if type(calibration) == list:
								calibration = torch.tensor(calibration, dtype=torch.float64)
							else:
								try:
									calibration = torch.tensor(calibration, dtype=torch.float64)
								except:
									raise ValueError("Failed to convert calibration data to tensor: " + str(calibration))
							if type(measurement) == list:
								measurement = torch.tensor(measurement, dtype=torch.float64)
							else:
								try:
									measurement = torch.tensor(measurement, dtype=torch.float64)
								except:
									raise ValueError("Failed to convert measurement data to tensor: " + str(measurement))
						# Append the data to the data object
						data_object.extend(calibration)
						data_object.extend(measurement)
					else:
						# Throw an error if the data is missing
						raise ValueError("Missing data for measurement type: " + measurement_type)
			# Append the data object to the data array
			data_object = torch.stack(data_object)
			weight = float(weight)
			# Normalize and map the weight to the given range (considering a max weight of 1000g) if needed
			if normalize_sensor_data:
				weight = (weight - 0) * (normalization_range[1] - normalization_range[0]) / (self.max_weight - 0) + normalization_range[0]
			weight = torch.tensor([weight], dtype=torch.float64)
			data.append((data_object, weight))
		# Shuffle the data
		random.shuffle(data)
		# Define the training and test datasets
		train_size = int(data_split[0] * len(data))
		val_size = int(data_split[1] * len(data))
		test_size = len(data) - train_size - val_size
		train_data = data[:train_size]
		val_data = data[train_size+val_size:]
		test_data = data[train_size:train_size+val_size]
		return train_data, val_data, test_data
	
	# Load the training data
	def train_dataloader(self): 
		return DataLoader(self.train_data, batch_size=self.batch_size)

	# Load the validation data
	def val_dataloader(self): 
		return DataLoader(self.test_data, batch_size=self.batch_size)
	
	# Load the test data
	def test_dataloader(self): 
		return DataLoader(self.test_data, batch_size=self.batch_size)
	
	# Get back the weight from the normalized weight value
	def get_weight(self, normalized_weight):
		# Get the weight from the normalized weight value
		weight = (normalized_weight - self.normalization_range[0]) * (self.max_weight - 0) / (self.normalization_range[1] - self.normalization_range[0]) + 0
		return weight

# Returns the data read from the calibration and measurement files in the corresponding model data directory
def get_model_data(refresh=False):
	# Read data from files in the "model_data" directory
	measurements_dir = "./measurements"
	data_dir = "./data"
	calibration_data = []
	measurements_data = []
	# Check if data already exists as "measurements.json" and "calibration.json" files in the data directory
	if not refresh and os.path.exists(os.path.join(data_dir, "measurements.json")) and os.path.exists(os.path.join(data_dir, "calibration.json")):
		# Read the data from the files
		with open(os.path.join(data_dir, "measurements.json"), "r") as f:
			measurements_data = json.load(f)
		with open(os.path.join(data_dir, "calibration.json"), "r") as f:
			calibration_data = json.load(f)
		return calibration_data, measurements_data
	else:
		# Build the data dictionaryes, iterating over all files in the directory and getting the corresponding measurements/calibration data
		files = os.listdir(measurements_dir)
		for file in tqdm.tqdm(files, desc="\nReading data from files...",position=0):
			if file.endswith(".txt"):
				is_calibration = file.startswith("calibration_")
				is_measurement = file.startswith("measurements_")
				if not is_calibration and not is_measurement:
					continue
				data_object = {}
				with open(os.path.join(measurements_dir, file), "r") as f:
					lines = f.readlines()
					data_object["sensor_datas"] = []
					for i in range(len(lines)):
						# Get the line text
						line = lines[i]
						if line == "\n" or line == "" or line == " ":
							continue
						# Check the line data type
						if i == 0:
							# Line contains the weight of the measured object
							data_object["weight"] = float(line)
						elif i == 1:
							# Line contains the name of the corresponding calibration file (as "calibration_<number>.txt")
							data_object["calibration_index"] = int(line.split("_")[1].split(".")[0]) - 1
							# data_object["calibration_file"] = line.strip()
						else:
							# Line contains the sensor data
							sensor_measurement_data = {}
							sensor_measurement_data["accelerometer"] = tuple(map(float, line.split(";")[0].split(",")))
							sensor_measurement_data["deviceTemperature"] = float(line.split(";")[1])
							sensor_measurement_data["gravity"] = tuple(map(float, line.split(";")[2].split(",")))
							sensor_measurement_data["gyroscope"] = tuple(map(float, line.split(";")[3].split(",")))
							sensor_measurement_data["linearAcceleration"] = tuple(map(float, line.split(";")[4].split(",")))
							sensor_measurement_data["orientation"] = tuple(map(float, line.split(";")[5].split(",")))
							sensor_measurement_data["pressure"] = float(line.split(";")[6])
							sensor_measurement_data["rotationVector"] = tuple(map(float, line.split(";")[7].split(",")))
							sensor_measurement_data["ambientTemperature"] = float(line.split(";")[8])
							data_object["sensor_datas"].append(sensor_measurement_data)
				# Add the data object to the corresponding list
				if is_calibration:
					calibration_data.append(data_object)
				elif is_measurement:
					measurements_data.append(data_object)
		# Save the data to files
		with open(os.path.join(data_dir, "measurements.json"), "w") as f:
			json.dump(measurements_data, f, indent=4)
		with open(os.path.join(data_dir, "calibration.json"), "w") as f:
			json.dump(calibration_data, f, indent=4)
		# Return the calibration and measurement data
		return calibration_data, measurements_data

def main(): 

	# General parameters
	REFRESH_DATA = True
	WANDB_API_KEY = "2ba6d81dbfe138d5c7fe13aeeeaac296cb88d274"
	# Model data parameters
	NORMALIZE_SENSOR_DATA = True
	NORMALIZATION_RANGE = [-1, 1]
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
	FNN_HIDDEN_LAYERS = 16
	FNN_LR = 0.00001
	# Model training parameters
	DATA_SPLIT = [0.9, 0.075, 0.025]	# Train, validation, test
	BATCH_SIZE = 16
	MAX_EPOCHS = 1_000

	# Path to save the model
	model_path = "./fnn.pth"

	# Seed the random number generator
	random_seed = 14
	random.seed(random_seed)
	torch.manual_seed(random_seed)

	# Set the device
	device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

	# Get the calibration and measurement data
	print("\nGetting the calibration and measurement data...")
	calibration_data, measurements_data = get_model_data(REFRESH_DATA)
	print("> DONE: Data loaded successfully (calibration: " + str(len(calibration_data)) + ", measurements: " + str(len(measurements_data)) + ")")

	# Define the Weights & Biases logger
	# Define the wandb logger, api object, entity name and project name
	wandb_project_name = "fnn"
	wandb_logger = None
	# wandb_api = None
	wandb_entity = None
	wandb_project = None
	# Check if a W&B api key is provided
	if WANDB_API_KEY == None or WANDB_API_KEY == "":
		print("\nNo W&B API key provided, logging with W&B disabled.")
	elif WANDB_API_KEY != "":
		# Login to the W&B (Weights & Biases) API
		wandb.login(key=WANDB_API_KEY, relogin=True)
		# Minimize the logging from the W&B (Weights & Biases) library
		os.environ["WANDB_SILENT"] = "true"
		os.environ["WANDB_MODE"] = "dryrun"
		logging.getLogger("wandb").setLevel(logging.ERROR)
		# Initialize the W&B (Weights & Biases) loggger
		wandb_logger = WandbLogger(log_model="all", project=wandb_project_name, name="- SEPARATOR -", offline=False)
		# Initialize the W&B (Weights & Biases) API
		# wandb_api = wandb.Api()
		# Get the W&B (Weights & Biases) entity name
		wandb_entity = wandb_logger.experiment.entity
		# Get the W&B (Weights & Biases) project name
		wandb_project = wandb_logger.experiment.project
		# Finish the "separator" experiment
		wandb_logger.experiment.finish(quiet=True)
		# Print the W&B (Weights & Biases) entity and project names, with also the W&B project dashboard URL
		print("\nW&B API key provided, logging with W&B enabled (entity: " + wandb_entity + ", project: " + wandb_project + ")\n> URL: https://wandb.ai/" + wandb_entity + "/" + wandb_project)

	# Define the data 
	print("\nInitializing the model data...")
	data = ModelData(
		measurements_data = measurements_data,
		calibration_data = calibration_data,
		normalize_sensor_data = NORMALIZE_SENSOR_DATA,
		normalization_range = NORMALIZATION_RANGE,
		data_split = DATA_SPLIT,
		batch_size = BATCH_SIZE,
		measurement_types = MEASUREMENTS,
		device = device
	)
	print("> DONE: Model data initialized successfully")

	# Define the model
	print("\nInitializing the FNN model...")
	model = FNN(
		input_dim = data.input_dim,
		hidden_dim = FNN_HIDDEN_DIM,
		hidden_layers = FNN_HIDDEN_LAYERS,
		output_dim = 1,
		lr = FNN_LR,
		device=device
	)
	print("> DONE: Model initialized successfully")

	# Restore the model from the file (if it exists)
	if os.path.exists(model_path):
		print("\nRestoring the model from file...")
		model.load_state_dict(torch.load(model_path))
		print("> DONE: Model restored successfully")

	# Train the model
	model_wandb_logger = None
	if wandb_logger != None:
		# Dont save log files locally 
		model_wandb_logger = WandbLogger(log_model="all", project=wandb_project, name="FNN", offline=False)
	print("\nTraining the model...")
	trainer = pl.Trainer(
		max_epochs=MAX_EPOCHS,
		num_sanity_val_steps=0,
		logger=model_wandb_logger,
		log_every_n_steps=-1,
		enable_checkpointing=False
	)
	trainer.fit(model, data)
	if wandb_logger != None:
		# Finish the "FNN" experiment
		model_wandb_logger.experiment.finish(quiet=True)
	print("\n> DONE: Model trained successfully")

	# Save model to file (to restore it later)
	print("\nSaving the model to file...")
	torch.save(model.state_dict(), model_path)

	# Test the model
	# print("\nTesting the model...")
	# trainer.test(model, data.test_dataloader())
	# print("\n> DONE: Model tested successfully")

	
if __name__ == "__main__":
	main()


