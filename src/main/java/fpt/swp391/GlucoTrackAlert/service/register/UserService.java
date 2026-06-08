package fpt.swp391.GlucoTrackAlert.service.register;

import fpt.swp391.GlucoTrackAlert.dto.login.LoginRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.LoginResponse;
import fpt.swp391.GlucoTrackAlert.dto.register.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;

public interface UserService {

    User register(RegisterRequest request) throws Exception;

    User activateUser(String token) throws Exception;

    LoginResponse login(LoginRequest request) throws Exception;

    void changePassword(String email, String oldPassword, String newPassword) throws Exception;
}
