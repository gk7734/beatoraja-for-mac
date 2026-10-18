package bms.player.beatoraja.metal;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.badlogic.gdx.graphics.GL20;

/** Forwards every GL20 call to the backend implementation (generated from the GL20 interface). */
class GL20Delegate implements GL20 {
	protected final GL20 gl;

	GL20Delegate(GL20 gl) {
		this.gl = gl;
	}

	public void glActiveTexture(int p0) {
		gl.glActiveTexture(p0);
	}

	public void glBindTexture(int p0, int p1) {
		gl.glBindTexture(p0, p1);
	}

	public void glBlendFunc(int p0, int p1) {
		gl.glBlendFunc(p0, p1);
	}

	public void glClear(int p0) {
		gl.glClear(p0);
	}

	public void glClearColor(float p0, float p1, float p2, float p3) {
		gl.glClearColor(p0, p1, p2, p3);
	}

	public void glClearDepthf(float p0) {
		gl.glClearDepthf(p0);
	}

	public void glClearStencil(int p0) {
		gl.glClearStencil(p0);
	}

	public void glColorMask(boolean p0, boolean p1, boolean p2, boolean p3) {
		gl.glColorMask(p0, p1, p2, p3);
	}

	public void glCompressedTexImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, Buffer p7) {
		gl.glCompressedTexImage2D(p0, p1, p2, p3, p4, p5, p6, p7);
	}

	public void glCompressedTexSubImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, Buffer p8) {
		gl.glCompressedTexSubImage2D(p0, p1, p2, p3, p4, p5, p6, p7, p8);
	}

	public void glCopyTexImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7) {
		gl.glCopyTexImage2D(p0, p1, p2, p3, p4, p5, p6, p7);
	}

	public void glCopyTexSubImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7) {
		gl.glCopyTexSubImage2D(p0, p1, p2, p3, p4, p5, p6, p7);
	}

	public void glCullFace(int p0) {
		gl.glCullFace(p0);
	}

	public void glDeleteTextures(int p0, IntBuffer p1) {
		gl.glDeleteTextures(p0, p1);
	}

	public void glDeleteTexture(int p0) {
		gl.glDeleteTexture(p0);
	}

	public void glDepthFunc(int p0) {
		gl.glDepthFunc(p0);
	}

	public void glDepthMask(boolean p0) {
		gl.glDepthMask(p0);
	}

	public void glDepthRangef(float p0, float p1) {
		gl.glDepthRangef(p0, p1);
	}

	public void glDisable(int p0) {
		gl.glDisable(p0);
	}

	public void glDrawArrays(int p0, int p1, int p2) {
		gl.glDrawArrays(p0, p1, p2);
	}

	public void glDrawElements(int p0, int p1, int p2, Buffer p3) {
		gl.glDrawElements(p0, p1, p2, p3);
	}

	public void glEnable(int p0) {
		gl.glEnable(p0);
	}

	public void glFinish() {
		gl.glFinish();
	}

	public void glFlush() {
		gl.glFlush();
	}

	public void glFrontFace(int p0) {
		gl.glFrontFace(p0);
	}

	public void glGenTextures(int p0, IntBuffer p1) {
		gl.glGenTextures(p0, p1);
	}

	public int glGenTexture() {
		return gl.glGenTexture();
	}

	public int glGetError() {
		return gl.glGetError();
	}

	public void glGetIntegerv(int p0, IntBuffer p1) {
		gl.glGetIntegerv(p0, p1);
	}

	public String glGetString(int p0) {
		return gl.glGetString(p0);
	}

	public void glHint(int p0, int p1) {
		gl.glHint(p0, p1);
	}

	public void glLineWidth(float p0) {
		gl.glLineWidth(p0);
	}

	public void glPixelStorei(int p0, int p1) {
		gl.glPixelStorei(p0, p1);
	}

	public void glPolygonOffset(float p0, float p1) {
		gl.glPolygonOffset(p0, p1);
	}

	public void glReadPixels(int p0, int p1, int p2, int p3, int p4, int p5, Buffer p6) {
		gl.glReadPixels(p0, p1, p2, p3, p4, p5, p6);
	}

	public void glScissor(int p0, int p1, int p2, int p3) {
		gl.glScissor(p0, p1, p2, p3);
	}

	public void glStencilFunc(int p0, int p1, int p2) {
		gl.glStencilFunc(p0, p1, p2);
	}

	public void glStencilMask(int p0) {
		gl.glStencilMask(p0);
	}

	public void glStencilOp(int p0, int p1, int p2) {
		gl.glStencilOp(p0, p1, p2);
	}

	public void glTexImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, Buffer p8) {
		gl.glTexImage2D(p0, p1, p2, p3, p4, p5, p6, p7, p8);
	}

	public void glTexParameterf(int p0, int p1, float p2) {
		gl.glTexParameterf(p0, p1, p2);
	}

	public void glTexSubImage2D(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, Buffer p8) {
		gl.glTexSubImage2D(p0, p1, p2, p3, p4, p5, p6, p7, p8);
	}

	public void glViewport(int p0, int p1, int p2, int p3) {
		gl.glViewport(p0, p1, p2, p3);
	}

	public void glAttachShader(int p0, int p1) {
		gl.glAttachShader(p0, p1);
	}

	public void glBindAttribLocation(int p0, int p1, String p2) {
		gl.glBindAttribLocation(p0, p1, p2);
	}

	public void glBindBuffer(int p0, int p1) {
		gl.glBindBuffer(p0, p1);
	}

	public void glBindFramebuffer(int p0, int p1) {
		gl.glBindFramebuffer(p0, p1);
	}

	public void glBindRenderbuffer(int p0, int p1) {
		gl.glBindRenderbuffer(p0, p1);
	}

	public void glBlendColor(float p0, float p1, float p2, float p3) {
		gl.glBlendColor(p0, p1, p2, p3);
	}

	public void glBlendEquation(int p0) {
		gl.glBlendEquation(p0);
	}

	public void glBlendEquationSeparate(int p0, int p1) {
		gl.glBlendEquationSeparate(p0, p1);
	}

	public void glBlendFuncSeparate(int p0, int p1, int p2, int p3) {
		gl.glBlendFuncSeparate(p0, p1, p2, p3);
	}

	public void glBufferData(int p0, int p1, Buffer p2, int p3) {
		gl.glBufferData(p0, p1, p2, p3);
	}

	public void glBufferSubData(int p0, int p1, int p2, Buffer p3) {
		gl.glBufferSubData(p0, p1, p2, p3);
	}

	public int glCheckFramebufferStatus(int p0) {
		return gl.glCheckFramebufferStatus(p0);
	}

	public void glCompileShader(int p0) {
		gl.glCompileShader(p0);
	}

	public int glCreateProgram() {
		return gl.glCreateProgram();
	}

	public int glCreateShader(int p0) {
		return gl.glCreateShader(p0);
	}

	public void glDeleteBuffer(int p0) {
		gl.glDeleteBuffer(p0);
	}

	public void glDeleteBuffers(int p0, IntBuffer p1) {
		gl.glDeleteBuffers(p0, p1);
	}

	public void glDeleteFramebuffer(int p0) {
		gl.glDeleteFramebuffer(p0);
	}

	public void glDeleteFramebuffers(int p0, IntBuffer p1) {
		gl.glDeleteFramebuffers(p0, p1);
	}

	public void glDeleteProgram(int p0) {
		gl.glDeleteProgram(p0);
	}

	public void glDeleteRenderbuffer(int p0) {
		gl.glDeleteRenderbuffer(p0);
	}

	public void glDeleteRenderbuffers(int p0, IntBuffer p1) {
		gl.glDeleteRenderbuffers(p0, p1);
	}

	public void glDeleteShader(int p0) {
		gl.glDeleteShader(p0);
	}

	public void glDetachShader(int p0, int p1) {
		gl.glDetachShader(p0, p1);
	}

	public void glDisableVertexAttribArray(int p0) {
		gl.glDisableVertexAttribArray(p0);
	}

	public void glDrawElements(int p0, int p1, int p2, int p3) {
		gl.glDrawElements(p0, p1, p2, p3);
	}

	public void glEnableVertexAttribArray(int p0) {
		gl.glEnableVertexAttribArray(p0);
	}

	public void glFramebufferRenderbuffer(int p0, int p1, int p2, int p3) {
		gl.glFramebufferRenderbuffer(p0, p1, p2, p3);
	}

	public void glFramebufferTexture2D(int p0, int p1, int p2, int p3, int p4) {
		gl.glFramebufferTexture2D(p0, p1, p2, p3, p4);
	}

	public int glGenBuffer() {
		return gl.glGenBuffer();
	}

	public void glGenBuffers(int p0, IntBuffer p1) {
		gl.glGenBuffers(p0, p1);
	}

	public void glGenerateMipmap(int p0) {
		gl.glGenerateMipmap(p0);
	}

	public int glGenFramebuffer() {
		return gl.glGenFramebuffer();
	}

	public void glGenFramebuffers(int p0, IntBuffer p1) {
		gl.glGenFramebuffers(p0, p1);
	}

	public int glGenRenderbuffer() {
		return gl.glGenRenderbuffer();
	}

	public void glGenRenderbuffers(int p0, IntBuffer p1) {
		gl.glGenRenderbuffers(p0, p1);
	}

	public String glGetActiveAttrib(int p0, int p1, IntBuffer p2, IntBuffer p3) {
		return gl.glGetActiveAttrib(p0, p1, p2, p3);
	}

	public String glGetActiveUniform(int p0, int p1, IntBuffer p2, IntBuffer p3) {
		return gl.glGetActiveUniform(p0, p1, p2, p3);
	}

	public void glGetAttachedShaders(int p0, int p1, Buffer p2, IntBuffer p3) {
		gl.glGetAttachedShaders(p0, p1, p2, p3);
	}

	public int glGetAttribLocation(int p0, String p1) {
		return gl.glGetAttribLocation(p0, p1);
	}

	public void glGetBooleanv(int p0, Buffer p1) {
		gl.glGetBooleanv(p0, p1);
	}

	public void glGetBufferParameteriv(int p0, int p1, IntBuffer p2) {
		gl.glGetBufferParameteriv(p0, p1, p2);
	}

	public void glGetFloatv(int p0, FloatBuffer p1) {
		gl.glGetFloatv(p0, p1);
	}

	public void glGetFramebufferAttachmentParameteriv(int p0, int p1, int p2, IntBuffer p3) {
		gl.glGetFramebufferAttachmentParameteriv(p0, p1, p2, p3);
	}

	public void glGetProgramiv(int p0, int p1, IntBuffer p2) {
		gl.glGetProgramiv(p0, p1, p2);
	}

	public String glGetProgramInfoLog(int p0) {
		return gl.glGetProgramInfoLog(p0);
	}

	public void glGetRenderbufferParameteriv(int p0, int p1, IntBuffer p2) {
		gl.glGetRenderbufferParameteriv(p0, p1, p2);
	}

	public void glGetShaderiv(int p0, int p1, IntBuffer p2) {
		gl.glGetShaderiv(p0, p1, p2);
	}

	public String glGetShaderInfoLog(int p0) {
		return gl.glGetShaderInfoLog(p0);
	}

	public void glGetShaderPrecisionFormat(int p0, int p1, IntBuffer p2, IntBuffer p3) {
		gl.glGetShaderPrecisionFormat(p0, p1, p2, p3);
	}

	public void glGetTexParameterfv(int p0, int p1, FloatBuffer p2) {
		gl.glGetTexParameterfv(p0, p1, p2);
	}

	public void glGetTexParameteriv(int p0, int p1, IntBuffer p2) {
		gl.glGetTexParameteriv(p0, p1, p2);
	}

	public void glGetUniformfv(int p0, int p1, FloatBuffer p2) {
		gl.glGetUniformfv(p0, p1, p2);
	}

	public void glGetUniformiv(int p0, int p1, IntBuffer p2) {
		gl.glGetUniformiv(p0, p1, p2);
	}

	public int glGetUniformLocation(int p0, String p1) {
		return gl.glGetUniformLocation(p0, p1);
	}

	public void glGetVertexAttribfv(int p0, int p1, FloatBuffer p2) {
		gl.glGetVertexAttribfv(p0, p1, p2);
	}

	public void glGetVertexAttribiv(int p0, int p1, IntBuffer p2) {
		gl.glGetVertexAttribiv(p0, p1, p2);
	}

	public void glGetVertexAttribPointerv(int p0, int p1, Buffer p2) {
		gl.glGetVertexAttribPointerv(p0, p1, p2);
	}

	public boolean glIsBuffer(int p0) {
		return gl.glIsBuffer(p0);
	}

	public boolean glIsEnabled(int p0) {
		return gl.glIsEnabled(p0);
	}

	public boolean glIsFramebuffer(int p0) {
		return gl.glIsFramebuffer(p0);
	}

	public boolean glIsProgram(int p0) {
		return gl.glIsProgram(p0);
	}

	public boolean glIsRenderbuffer(int p0) {
		return gl.glIsRenderbuffer(p0);
	}

	public boolean glIsShader(int p0) {
		return gl.glIsShader(p0);
	}

	public boolean glIsTexture(int p0) {
		return gl.glIsTexture(p0);
	}

	public void glLinkProgram(int p0) {
		gl.glLinkProgram(p0);
	}

	public void glReleaseShaderCompiler() {
		gl.glReleaseShaderCompiler();
	}

	public void glRenderbufferStorage(int p0, int p1, int p2, int p3) {
		gl.glRenderbufferStorage(p0, p1, p2, p3);
	}

	public void glSampleCoverage(float p0, boolean p1) {
		gl.glSampleCoverage(p0, p1);
	}

	public void glShaderBinary(int p0, IntBuffer p1, int p2, Buffer p3, int p4) {
		gl.glShaderBinary(p0, p1, p2, p3, p4);
	}

	public void glShaderSource(int p0, String p1) {
		gl.glShaderSource(p0, p1);
	}

	public void glStencilFuncSeparate(int p0, int p1, int p2, int p3) {
		gl.glStencilFuncSeparate(p0, p1, p2, p3);
	}

	public void glStencilMaskSeparate(int p0, int p1) {
		gl.glStencilMaskSeparate(p0, p1);
	}

	public void glStencilOpSeparate(int p0, int p1, int p2, int p3) {
		gl.glStencilOpSeparate(p0, p1, p2, p3);
	}

	public void glTexParameterfv(int p0, int p1, FloatBuffer p2) {
		gl.glTexParameterfv(p0, p1, p2);
	}

	public void glTexParameteri(int p0, int p1, int p2) {
		gl.glTexParameteri(p0, p1, p2);
	}

	public void glTexParameteriv(int p0, int p1, IntBuffer p2) {
		gl.glTexParameteriv(p0, p1, p2);
	}

	public void glUniform1f(int p0, float p1) {
		gl.glUniform1f(p0, p1);
	}

	public void glUniform1fv(int p0, int p1, FloatBuffer p2) {
		gl.glUniform1fv(p0, p1, p2);
	}

	public void glUniform1fv(int p0, int p1, float[] p2, int p3) {
		gl.glUniform1fv(p0, p1, p2, p3);
	}

	public void glUniform1i(int p0, int p1) {
		gl.glUniform1i(p0, p1);
	}

	public void glUniform1iv(int p0, int p1, IntBuffer p2) {
		gl.glUniform1iv(p0, p1, p2);
	}

	public void glUniform1iv(int p0, int p1, int[] p2, int p3) {
		gl.glUniform1iv(p0, p1, p2, p3);
	}

	public void glUniform2f(int p0, float p1, float p2) {
		gl.glUniform2f(p0, p1, p2);
	}

	public void glUniform2fv(int p0, int p1, FloatBuffer p2) {
		gl.glUniform2fv(p0, p1, p2);
	}

	public void glUniform2fv(int p0, int p1, float[] p2, int p3) {
		gl.glUniform2fv(p0, p1, p2, p3);
	}

	public void glUniform2i(int p0, int p1, int p2) {
		gl.glUniform2i(p0, p1, p2);
	}

	public void glUniform2iv(int p0, int p1, IntBuffer p2) {
		gl.glUniform2iv(p0, p1, p2);
	}

	public void glUniform2iv(int p0, int p1, int[] p2, int p3) {
		gl.glUniform2iv(p0, p1, p2, p3);
	}

	public void glUniform3f(int p0, float p1, float p2, float p3) {
		gl.glUniform3f(p0, p1, p2, p3);
	}

	public void glUniform3fv(int p0, int p1, FloatBuffer p2) {
		gl.glUniform3fv(p0, p1, p2);
	}

	public void glUniform3fv(int p0, int p1, float[] p2, int p3) {
		gl.glUniform3fv(p0, p1, p2, p3);
	}

	public void glUniform3i(int p0, int p1, int p2, int p3) {
		gl.glUniform3i(p0, p1, p2, p3);
	}

	public void glUniform3iv(int p0, int p1, IntBuffer p2) {
		gl.glUniform3iv(p0, p1, p2);
	}

	public void glUniform3iv(int p0, int p1, int[] p2, int p3) {
		gl.glUniform3iv(p0, p1, p2, p3);
	}

	public void glUniform4f(int p0, float p1, float p2, float p3, float p4) {
		gl.glUniform4f(p0, p1, p2, p3, p4);
	}

	public void glUniform4fv(int p0, int p1, FloatBuffer p2) {
		gl.glUniform4fv(p0, p1, p2);
	}

	public void glUniform4fv(int p0, int p1, float[] p2, int p3) {
		gl.glUniform4fv(p0, p1, p2, p3);
	}

	public void glUniform4i(int p0, int p1, int p2, int p3, int p4) {
		gl.glUniform4i(p0, p1, p2, p3, p4);
	}

	public void glUniform4iv(int p0, int p1, IntBuffer p2) {
		gl.glUniform4iv(p0, p1, p2);
	}

	public void glUniform4iv(int p0, int p1, int[] p2, int p3) {
		gl.glUniform4iv(p0, p1, p2, p3);
	}

	public void glUniformMatrix2fv(int p0, int p1, boolean p2, FloatBuffer p3) {
		gl.glUniformMatrix2fv(p0, p1, p2, p3);
	}

	public void glUniformMatrix2fv(int p0, int p1, boolean p2, float[] p3, int p4) {
		gl.glUniformMatrix2fv(p0, p1, p2, p3, p4);
	}

	public void glUniformMatrix3fv(int p0, int p1, boolean p2, FloatBuffer p3) {
		gl.glUniformMatrix3fv(p0, p1, p2, p3);
	}

	public void glUniformMatrix3fv(int p0, int p1, boolean p2, float[] p3, int p4) {
		gl.glUniformMatrix3fv(p0, p1, p2, p3, p4);
	}

	public void glUniformMatrix4fv(int p0, int p1, boolean p2, FloatBuffer p3) {
		gl.glUniformMatrix4fv(p0, p1, p2, p3);
	}

	public void glUniformMatrix4fv(int p0, int p1, boolean p2, float[] p3, int p4) {
		gl.glUniformMatrix4fv(p0, p1, p2, p3, p4);
	}

	public void glUseProgram(int p0) {
		gl.glUseProgram(p0);
	}

	public void glValidateProgram(int p0) {
		gl.glValidateProgram(p0);
	}

	public void glVertexAttrib1f(int p0, float p1) {
		gl.glVertexAttrib1f(p0, p1);
	}

	public void glVertexAttrib1fv(int p0, FloatBuffer p1) {
		gl.glVertexAttrib1fv(p0, p1);
	}

	public void glVertexAttrib2f(int p0, float p1, float p2) {
		gl.glVertexAttrib2f(p0, p1, p2);
	}

	public void glVertexAttrib2fv(int p0, FloatBuffer p1) {
		gl.glVertexAttrib2fv(p0, p1);
	}

	public void glVertexAttrib3f(int p0, float p1, float p2, float p3) {
		gl.glVertexAttrib3f(p0, p1, p2, p3);
	}

	public void glVertexAttrib3fv(int p0, FloatBuffer p1) {
		gl.glVertexAttrib3fv(p0, p1);
	}

	public void glVertexAttrib4f(int p0, float p1, float p2, float p3, float p4) {
		gl.glVertexAttrib4f(p0, p1, p2, p3, p4);
	}

	public void glVertexAttrib4fv(int p0, FloatBuffer p1) {
		gl.glVertexAttrib4fv(p0, p1);
	}

	public void glVertexAttribPointer(int p0, int p1, int p2, boolean p3, int p4, Buffer p5) {
		gl.glVertexAttribPointer(p0, p1, p2, p3, p4, p5);
	}

	public void glVertexAttribPointer(int p0, int p1, int p2, boolean p3, int p4, int p5) {
		gl.glVertexAttribPointer(p0, p1, p2, p3, p4, p5);
	}
}
