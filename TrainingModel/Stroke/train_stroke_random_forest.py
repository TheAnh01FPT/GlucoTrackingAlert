import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score, recall_score
from imblearn.over_sampling import SMOTE
import joblib

print("--- STEP 1: READ AND CLEAN DATA ---")
# 1. Read data from CSV
df = pd.read_csv("dataset.csv")
print(f"-> Read dataset successfully: {df.shape[0]} rows, {df.shape[1]} columns.")

# 2. Handle missing BMI values with median
df['bmi'] = pd.to_numeric(df['bmi'], errors='coerce')
bmi_median = df['bmi'].median()
df['bmi'] = df['bmi'].fillna(bmi_median)
print(f"-> Filled missing BMI values with median: {bmi_median}")

# 3. Remove rows with 'Other' gender
df = df[df['gender'] != 'Other']


print("\n--- STEP 2: PREPROCESSING & ENCODING ---")
# 1. Drop 'id' column
df.drop(columns=['id'], inplace=True, errors='ignore')

# 2. Map categorical features to numerical values
# ever_married
df['ever_married'] = df['ever_married'].map({'Yes': 1, 'No': 0})

# gender
df['gender'] = df['gender'].map({'Male': 0, 'Female': 1})

# Residence_type
df['Residence_type'] = df['Residence_type'].map({'Rural': 0, 'Urban': 1})

# work_type
df['work_type'] = df['work_type'].map({
    'Private': 0, 
    'Self-employed': 1, 
    'Govt_job': 2, 
    'children': -1, 
    'Never_worked': -2
})

# smoking_status
df['smoking_status'] = df['smoking_status'].map({
    'never smoked': 0,
    'formerly smoked': 1,
    'smokes': 2,
    'Unknown': -1
})

# 3. Separate features (X) and target (y)
feature_cols = ['gender', 'age', 'hypertension', 'heart_disease', 'work_type', 'Residence_type', 'avg_glucose_level', 'bmi', 'smoking_status']
X = df[feature_cols]
y = df['stroke']

# 4. Train/Test split (70/30)
X_train, X_test, y_train, y_test = train_test_split(X, y, train_size=0.7, random_state=42, stratify=y)
print(f"-> Train set: {X_train.shape[0]} samples | Test set: {X_test.shape[0]} samples")


print("\n--- STEP 3: BALANCE DATA (SMOTE) ---")
# Apply SMOTE to training set
oversample = SMOTE(random_state=42)
X_train_resh, y_train_resh = oversample.fit_resample(X_train, y_train)
print(f"-> After SMOTE: Class 0 count: {sum(y_train_resh == 0)} | Class 1 count: {sum(y_train_resh == 1)}")


print("\n--- STEP 4: SCALE FEATURES & TRAIN RANDOM FOREST ---")
# 1. Scale features using StandardScaler
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train_resh)
X_test_scaled = scaler.transform(X_test)

# 2. Train Random Forest Classifier
rf_model = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42, class_weight='balanced')
rf_model.fit(X_train_scaled, y_train_resh)
print("-> Random Forest Classifier training completed.")


print("\n--- STEP 5: EVALUATE MODEL ---")
# 1. Predict labels
y_pred_binary = rf_model.predict(X_test_scaled)

# 2. Predict probability
y_pred_proba = rf_model.predict_proba(X_test_scaled)[:, 1]

# 3. Print evaluation metrics
print("\n=== CLASSIFICATION REPORT (Default 50% Threshold): ===")
print(classification_report(y_test, y_pred_binary))
print("Accuracy Score:", accuracy_score(y_test, y_pred_binary))
print("F1-Score:", f1_score(y_test, y_pred_binary))
print("Recall:", recall_score(y_test, y_pred_binary))

# 4. Print sample predictions
print("\n=== Sample predictions for the first 5 patients: ===")
for i in range(5):
    prob_percent = y_pred_proba[i] * 100
    actual = y_test.iloc[i]
    pred_label = y_pred_binary[i]
    print(f"Patient {i+1}: Predicted Risk: {prob_percent:.2f}% | Binary Prediction: {pred_label} | Actual: {actual}")


import os
from datetime import datetime

# Ensure output directories exist
os.makedirs("models", exist_ok=True)
os.makedirs("predictions", exist_ok=True)

print("\n--- STEP 6: SAVE MODEL & SCALER ---")
# Save model and scaler with timestamp to prevent overwriting
timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
model_filename = f'models/random_forest_stroke_model_{timestamp}.pkl'
scaler_filename = f'models/scaler_stroke_rf_{timestamp}.pkl'

joblib.dump(rf_model, model_filename)
joblib.dump(scaler, scaler_filename)
print(f"-> Saved model to '{model_filename}' and scaler to '{scaler_filename}' successfully!")

print("\n--- STEP 7: SAVE TEST PREDICTIONS TO CSV ---")
# Create a DataFrame with actual values, predicted labels, and risk percentages
test_results = pd.DataFrame({
    'Actual_Stroke': y_test,
    'Predicted_Stroke': y_pred_binary,
    'Stroke_Probability_Percent': y_pred_proba * 100
})

prediction_file = f'predictions/test_predictions_{timestamp}.csv'
test_results.to_csv(prediction_file, index=False)
print(f"-> Saved test prediction results to '{prediction_file}' successfully!")


