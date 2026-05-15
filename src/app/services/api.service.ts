import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api';

  // Dashboard
  getDashboardStats(): Observable<any> {
    return this.http.get(`${this.base}/dashboard/stats`);
  }
  getInstructionsRecentes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/dashboard/instructions-recentes`);
  }

  // Instructions / Threads
  getInstructions(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions`);
  }
  createInstruction(data: any): Observable<any> {
    return this.http.post(`${this.base}/instructions`, data);
  }

  // Messages
  getMessages(instructionId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions/${instructionId}/messages`);
  }
  sendMessage(instructionId: string, msg: any): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/messages`, msg);
  }
  validateMessage(msgId: string): Observable<any> {
    return this.http.patch(`${this.base}/instructions/messages/${msgId}/validate`, {});
  }
  rejectMessage(msgId: string): Observable<any> {
    return this.http.patch(`${this.base}/instructions/messages/${msgId}/reject`, {});
  }

  // Collaborateurs
  getCollaborateurs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/collaborateurs`);
  }
  addCollaborateur(data: any): Observable<any> {
    return this.http.post(`${this.base}/collaborateurs`, data);
  }
  deleteCollaborateur(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/collaborateurs/${id}`);
  }
}
