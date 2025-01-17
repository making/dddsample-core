package lol.maki.cargotracker.interfaces.tracking.web;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validator for {@link TrackCommand}s.
 */
@Component
public final class TrackCommandValidator implements Validator {

	public boolean supports(final Class clazz) {
		return TrackCommand.class.isAssignableFrom(clazz);
	}

	public void validate(final Object object, final Errors errors) {
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "trackingId", "error.required", "Required");
	}

}
