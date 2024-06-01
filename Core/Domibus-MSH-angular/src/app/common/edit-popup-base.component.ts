import {Component, Inject, ViewChild} from '@angular/core';
import {AbstractControl, UntypedFormGroup, NgControl, NgForm} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {UserValidatorService} from '../user/support/uservalidator.service';

/**
 * @author Ion Perpegel
 * @since 4.2
 *
 * Base class representing an edit popup form component
 * More common functionality will be added in time
 */

@Component({
  template: '',
})

export class EditPopupBaseComponent {

  @ViewChild('editForm')
  public editForm: NgForm | UntypedFormGroup;

  userNamePattern = UserValidatorService.USER_NAME_PATTERN;
  userNamePatternMessage = UserValidatorService.USER_NAME_PATTERN_MESSAGE;
  userNameMinLengthMessage = UserValidatorService.USER_NAME_MINLENGTH_MESSAGE;
  userNameRequiredMessage = UserValidatorService.USER_NAME_REQUIRED_MESSAGE;

  public constructor(public dialogRef: MatDialogRef<any>, @Inject(MAT_DIALOG_DATA) public data: any) {
  }

  // method to be overridden in derived classes to add specific behaviour on submit
  onSubmitForm() {
  }

  public submitForm() {
    if (this.isFormDisabled()) {
      return;
    }

    this.onSubmitForm();

    this.dialogRef.close(this.getDialogResult());
  }

  protected getDialogResult(): any {
    return true;
  }

  public shouldShowErrors(field: NgControl | NgForm | AbstractControl): boolean {
    return (field.touched || field.dirty) && !!field.errors;
  }

  public isFormDisabled() {
    return !this.editForm || this.editForm.invalid || !this.editForm.dirty;
  }

}
