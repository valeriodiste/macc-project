
# For more info: https://www.geeksforgeeks.org/training-neural-networks-using-pytorch-lightning/

# Import the required libraries
import random
import math
import torch
import torch.nn as nn
import pytorch_lightning as pl
from torch.utils.data import DataLoader 

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
			max_weight,
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
		self.max_weight = max_weight
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
				# Convert the measurements to tensors
				calibration_tensors_list = self.format_measurements(single_calibration_sensor_data, measurement_types, normalize_sensor_data, normalization_range)
				measurement_tensors_list = self.format_measurements(single_measurement_sensors_data, measurement_types, normalize_sensor_data, normalization_range)
				# Extend the data object
				data_object.extend(calibration_tensors_list)
				data_object.extend(measurement_tensors_list)
			# Convert the data object to a tensor
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
	
	# Static function to convert an array of measurements into a tensor of measurements in the correct normalized format
	@staticmethod
	def format_measurements(measurements, measurement_types, normalize_sensor_data, normalization_range):
		# Initialize the return tensor
		measurements_tensors_list = []
		# Initialize the measurement tensor
		measurement = None
		# Iterate over all measurement types
		for measurement_type in measurement_types:
			if measurements[measurement_type] is not None:
				# Get the calibration and measurement data
				measurement = measurements[measurement_type]
				# Normalize and convert data to tensor (if needed)
				if normalize_sensor_data:
					if measurement_type == "accelerometer":
						# Accelerometer is a <x, y, z> tuple with coordinates in m/s^2 (safely assume data is in ranges [-10, 10], normalize to [-1, 1])
						measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
					elif measurement_type == "deviceTemperature":
						# Device temperature is a float value in Celsius (safely assume data is in ranges [0, 50], normalize to [-1, 1])
						measurement = torch.tensor([(measurement+50)/100.0], dtype=torch.float64)
					elif measurement_type == "gravity":
						# Gravity is a <x, y, z> tuple with coordinates in m/s^2 (safely assume data is in ranges [-10, 10], normalize to [-1, 1], since gravity on Earth is 9.8 m/s^2 on average, never varying by more than 0.2, and can be negative or positive depending on the orientation)
						measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
					elif measurement_type == "gyroscope":
						# Gyroscope is a <x, y, z> tuple with coordinates in rad/s (we can assume data to be 360 degrees per second at most, in both directions, normalize to [-1, 1])
						measurement = torch.tensor([val/(2*math.pi) for val in measurement], dtype=torch.float64)
					elif measurement_type == "linearAcceleration":
						# Linear acceleration is a <x, y, z> tuple with coordinates in m/s^2 (during measurements, acceleration should be small, assume data already in range [-10, 10], normalize to [-1, 1])
						measurement = torch.tensor([val/10.0 for val in measurement], dtype=torch.float64)
					elif measurement_type == "orientation":
						# Orientation is a <x, y, z> tuple with coordinates in degrees (safely assume data is in ranges [-180, 180], normalize to [-1, 1])
						measurement = torch.tensor([val/180.0 for val in measurement], dtype=torch.float64)
					elif measurement_type == "pressure":
						# Pressure is a float value in hPa (safely assume data is in ranges [900, 1100], normalize to [-1, 1])
						measurement = torch.tensor([(measurement-900)/200.0], dtype=torch.float64)
					elif measurement_type == "rotationVector":
						# Rotation vector is a <x, y, z> tuple with coordinates in unitless values (safely assume data is in ranges [-1, 1], no need to normalize to [-1, 1])
						measurement = torch.tensor([val for val in measurement], dtype=torch.float64)
					elif measurement_type == "ambientTemperature":
						# Ambient temperature is a float value in Celsius (safely assume data is in ranges [0, 50], normalize to [-1, 1])
						measurement = torch.tensor([(measurement+50)/100.0], dtype=torch.float64)
					else:
						# Throw an error if the measurement type is not recognized
						raise ValueError("Unknown measurement type: " + measurement_type)
					# Take all data normalized in range [-1,1] and map it to a new range
					measurement = (measurement - (-1)) * (normalization_range[1] - normalization_range[0]) / (1 - (-1)) + normalization_range[0]
				else:
					# Convert data to tensor (without normalization)
					if type(measurement) == list:
						measurement = torch.tensor(measurement, dtype=torch.float64)
					else:
						try:
							measurement = torch.tensor(measurement, dtype=torch.float64)
						except:
							raise ValueError("Failed to convert measurement data to tensor: " + str(measurement))
				# Append the measurement to the measurements tensor
				measurements_tensors_list.extend(measurement)
			else:
				# Throw an error if the data is missing
				raise ValueError("Missing data for measurement type: " + measurement_type)
		# Return the measurements tensor 
		return measurements_tensors_list

	# Load the training data
	def train_dataloader(self): 
		return DataLoader(self.train_data, batch_size=self.batch_size)

	# Load the validation data
	def val_dataloader(self): 
		return DataLoader(self.val_data, batch_size=self.batch_size)
	
	# Load the test data
	def test_dataloader(self): 
		return DataLoader(self.test_data, batch_size=self.batch_size)
	
	# Get back the weight from the normalized weight value
	@staticmethod
	def get_weight(value, normalize_data, normalization_range, max_weight):
		# Get the weight from the normalized weight value
		# weight = (normalized_weight - self.normalization_range[0]) * (self.max_weight - 0) / (self.normalization_range[1] - self.normalization_range[0]) + 0
		if normalize_data:
			weight = (value - normalization_range[0]) * (max_weight - 0) / (normalization_range[1] - normalization_range[0]) + 0
		else:
			weight = value
		return weight

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
			activation_fn = "ReLU",
			dropout: float = 0.5,
			optimizer = "Adam",
			lr: float = 0.01,
			loss_fn = nn.MSELoss(),
			device = torch.device("cuda" if torch.cuda.is_available() else "cpu"),
			normalize_data = True,
			normalization_range = [-2,2],
			max_weight = 750,
			log_on_wandb = True,
			log_on_console = False,
			# Optional arguments
			**kwargs
		): 

		super(FNN, self).__init__() 

		# Define the hyperparameters
		self.input_dim = input_dim
		self.hidden_dim = hidden_dim
		self.hidden_layers = hidden_layers
		self.output_dim = output_dim
		self.activation_fn = activation_fn
		self.dropout = dropout
		self.optimizer = optimizer
		self.lr = lr
		self.loss = loss_fn
		self.normalize_data = normalize_data
		self.normalization_range = normalization_range
		self.max_weight = max_weight
		self.kwargs = kwargs
		# self.device = device
		self.log_on_wandb = log_on_wandb
		self.log_on_console = log_on_console
		# self.save_hyperparameters()

		# self.save_hyperparameters(
		# 	{
		# 		"input_dim": input_dim,
		# 		"hidden_dim": hidden_dim,
		# 		"hidden_layers": hidden_layers,
		# 		"output_dim": output_dim,
		# 		"activation_fn": activation_fn,
		# 		"dropout": dropout,
		# 		"optimizer": optimizer,
		# 		"lr": lr,
		# 		"loss": loss_fn
		# 	},
		# 	logger=self.logger
		# )

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
		self.layers.append({
				"ReLU": nn.ReLU(),
				"LeakyReLU": nn.LeakyReLU(),
				"Tanh": nn.Tanh(),
				"Sigmoid": nn.Sigmoid()
			}[self.activation_fn])
		self.layers.append(nn.Dropout(p=dropout))
		for _ in range(self.hidden_layers):
			self.layers.append(nn.Linear(self.hidden_dim, self.hidden_dim, dtype=torch.float64))
			self.layers.append({
				"ReLU": nn.ReLU(),
				"LeakyReLU": nn.LeakyReLU(),
				"Tanh": nn.Tanh(),
				"Sigmoid": nn.Sigmoid()
			}[self.activation_fn])
			self.layers.append(nn.Dropout(p=dropout))
		self.layers.append(nn.Linear(self.hidden_dim, self.output_dim, dtype=torch.float64))
		self.model = nn.Sequential(*self.layers)
		self.model.to(device)

		# store variables and lists of losses and accuracies for logging
		self.train_losses = []
		self.val_losses = []
		self.train_accuracies = []
		self.val_accuracies = []
		self.test_losses = []
		self.test_accuracies = []

	def forward(self, x): 
		return self.model(x)

	def configure_optimizers(self): 
		# Define the optimizer
		optimizer = {
			"Adam": torch.optim.Adam(self.model.parameters(), lr=self.lr),
			"SGD": torch.optim.SGD(self.model.parameters(), lr=self.lr),
			"RMSprop": torch.optim.RMSprop(self.model.parameters(), lr=self.lr),
			"Adadelta": torch.optim.Adadelta(self.model.parameters(), lr=self.lr),
			"Adagrad": torch.optim.Adagrad(self.model.parameters(), lr=self.lr),
			"AdamW": torch.optim.AdamW(self.model.parameters(), lr=self.lr),
			"Adamax": torch.optim.Adamax(self.model.parameters(), lr=self.lr),
			"ASGD": torch.optim.ASGD(self.model.parameters(), lr=self.lr),
			"LBFGS": torch.optim.LBFGS(self.model.parameters(), lr=self.lr),
			"Rprop": torch.optim.Rprop(self.model.parameters(), lr=self.lr)
		}[self.optimizer]
		return optimizer
	
	def _step(self, batch, batch_idx):
		# Get the data and the target
		data, target = batch
		# Forward pass
		output = self.model(data)
		# Calculate the loss
		loss = self.loss(output, target)
		# Calculate the accuracy
		mae = torch.mean(torch.abs(target - output))	# In range [self.normalization_range[0], self.normalization_range[1]]
		accuracy = 1.0 - mae / (self.normalization_range[1] - self.normalization_range[0])
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
				if self.log_on_console:
					print("\nEpoch: ", epoch)
			if len(self.train_losses)>0:
				train_loss = torch.stack(self.train_losses).mean()
				train_accuracy = torch.stack(self.train_accuracies).mean()
				if self.log_on_wandb:
					self.log("train_loss", train_loss)
					self.log("train_accuracy", train_accuracy)
				if self.log_on_console:
					print("Train loss: ", round(train_loss.item(), 4), " | Train accuracy: ", round(train_accuracy.item(), 4))
			if len(self.val_losses)>0:
				val_loss = torch.stack(self.val_losses).mean()
				val_accuracy = torch.stack(self.val_accuracies).mean()
				if self.log_on_wandb:
					self.log("val_loss", val_loss)
					self.log("val_accuracy", val_accuracy)
				if self.log_on_console:
					print("Validation loss: ", round(val_loss.item(), 4), " | Validation accuracy: ", round(val_accuracy.item(), 4))
			if len(self.test_losses)>0:
				test_loss = torch.stack(self.test_losses).mean()
				test_accuracy = torch.stack(self.test_accuracies).mean()
				if self.log_on_wandb:
					self.log("test_loss", test_loss)
					self.log("test_accuracy", test_accuracy)
				if self.log_on_console:
					print("Test loss: ", round(test_loss.item(), 4), " | Test accuracy: ", round(test_accuracy.item(), 4))
	
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

	def predict(self, data):
		# Set the model to evaluation mode if needed
		self.model.eval()
		# Forward pass
		with torch.no_grad():
			output = self.model(data)
		# Get the weight
		weight = ModelData.get_weight(output, self.normalize_data, self.normalization_range, self.max_weight)
		return weight, output
