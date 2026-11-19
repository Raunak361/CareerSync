package com.example.careerMonitor.DTO;

import com.example.careerMonitor.Entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Bean;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

public class UserRequestDto {
    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;



    public UserRequestDto(User user){
        this.name = user.getName();
        this.email = user.getEmail();

    }

    public String email() {
        return this.email;
    }

    public String name() {
        return this.name;
    }


}