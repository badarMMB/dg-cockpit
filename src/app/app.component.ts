import { Component, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NotificationService } from './services/notification.service';
import { ToastComponent } from './shared/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule, ToastComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private notifications = inject(NotificationService);

  ngOnInit() {
    this.notifications.connect();
  }
}
