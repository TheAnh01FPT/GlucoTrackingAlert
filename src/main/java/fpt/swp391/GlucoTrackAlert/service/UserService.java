package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.User;

public interface UserService {
    User register(RegisterRequest request) throws Exception;
    User activateUser(String token) throws Exception;
}

