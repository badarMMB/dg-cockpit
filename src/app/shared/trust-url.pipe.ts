import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Pipe({ name: 'trustUrl', standalone: true })
export class TrustUrlPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);
  transform(url: string | null): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(url ?? '');
  }
}
