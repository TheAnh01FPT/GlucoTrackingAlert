package fpt.swp391.GlucoTrackAlert.dto.login;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String token;
    private String email;
    private String role;
    private String message;
    private Long doctorId;
    private boolean requiresOtp;
}