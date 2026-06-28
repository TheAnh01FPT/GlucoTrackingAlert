import joblib
from fastapi import FastAPI
from pydantic import BaseModel
import numpy as np

# Load the trained Random Forest model and scaler from the models directory
model = joblib.load('models/random_forest_stroke_model.pkl')
scaler = joblib.load('models/scaler_stroke_rf.pkl')


app = FastAPI(title="GlucoTrackAlert AI Prediction Service")

class PatientData(BaseModel):
    gender: int
    age: float
    hypertension: int
    heart_disease: int
    work_type: int
    Residence_type: int  # Capital R matches the Java request
    avg_glucose_level: float
    bmi: float
    smoking_status: int

@app.post("/predict")
def predict_risk(data: PatientData):
    # Construct feature array in the correct order:
    # ['gender', 'age', 'hypertension', 'heart_disease', 'work_type', 'Residence_type', 'avg_glucose_level', 'bmi', 'smoking_status']
    features = np.array([[
        data.gender,
        data.age,
        data.hypertension,
        data.heart_disease,
        data.work_type,
        data.Residence_type,
        data.avg_glucose_level,
        data.bmi,
        data.smoking_status
    ]])
    
    # Scale features using the loaded scaler
    features_scaled = scaler.transform(features)
    
    # Predict probability of class 1 (stroke)
    prob = model.predict_proba(features_scaled)[0][1]
    risk_percentage = round(prob * 100, 2)
    
    # Determine risk level based on threshold
    if risk_percentage > 70.0:
        risk_level = "Critical"
    elif risk_percentage > 50.0:
        risk_level = "High"
    elif risk_percentage > 30.0:
        risk_level = "Medium"
    else:
        risk_level = "Low"
        
    print(f"Received request for age={data.age}, glucose={data.avg_glucose_level}, bmi={data.bmi} -> Predicted Risk: {risk_percentage}% ({risk_level})")
    
    return {
        "risk_percentage": risk_percentage,
        "risk_level": risk_level
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
