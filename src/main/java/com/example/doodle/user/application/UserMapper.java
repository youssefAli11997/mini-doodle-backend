package com.example.doodle.user.application;

import com.example.doodle.user.domain.User;
import com.example.doodle.user.persistence.UserEntity;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new User(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        return new UserEntity(
                domain.id(),
                domain.name(),
                domain.email(),
                domain.createdAt(),
                domain.updatedAt()
        );
    }

    public static UserDto toDto(User domain) {
        if (domain == null) {
            return null;
        }
        return new UserDto(
                domain.id(),
                domain.name(),
                domain.email(),
                domain.createdAt(),
                domain.updatedAt()
        );
    }
}
